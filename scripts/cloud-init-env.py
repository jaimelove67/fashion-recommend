"""Create a separate cloud environment without printing or replacing secrets."""

import argparse
import base64
import http.client
import json
import os
from pathlib import Path
import re
import secrets
import socket
import ssl
import subprocess
from urllib.parse import urlsplit


MODEL_SETTINGS = {
    "DASHSCOPE_API_KEY", "BAILIAN_ENABLED", "BAILIAN_MODEL", "BAILIAN_ENDPOINT",
    "BAILIAN_VISION_ENABLED", "BAILIAN_VISION_MODEL", "BAILIAN_IMAGE_ENABLED",
    "BAILIAN_IMAGE_MODEL", "BAILIAN_IMAGE_ENDPOINT", "BAILIAN_IMAGE_TASK_ENDPOINT",
    "BAILIAN_CONNECT_TIMEOUT", "BAILIAN_READ_TIMEOUT",
    "BAILIAN_PROFILE_ANALYSIS_READ_TIMEOUT", "BAILIAN_IMAGE_CONNECT_TIMEOUT",
    "BAILIAN_IMAGE_READ_TIMEOUT", "BAILIAN_IMAGE_TASK_TIMEOUT",
    "BAILIAN_IMAGE_POLL_INTERVAL",
}


def read_values(path):
    values = {}
    for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Z][A-Z0-9_]*)\s*=(.*)", line)
        if not match:
            raise ValueError(f"Invalid environment assignment at line {number}")
        name, value = match.groups()
        value = value.strip()
        if value[:1] in ("'", '"'):
            if len(value) < 2 or value[-1] != value[0]:
                raise ValueError(f"Unsupported multiline value for {name}")
            value = value[1:-1]
        else:
            value = re.split(r"\s+#", value, maxsplit=1)[0].rstrip()
        values[name] = value
    return values


def create_environment(template, output, model_env=None):
    values = read_values(template)
    values.update({
        "POSTGRES_PASSWORD": secrets.token_hex(24),
        "MINIO_ROOT_PASSWORD": secrets.token_hex(24),
        "AI_SETTINGS_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode("ascii"),
    })
    if model_env is not None:
        imported = read_values(model_env)
        values.update({name: value for name, value in imported.items() if name in MODEL_SETTINGS})
    lines = ["# Private deployment credentials. Back up securely; do not regenerate on updates."]
    for name, value in values.items():
        if any(character in value for character in ("'", "\r", "\n", "\x00")) or "${" in value:
            raise ValueError(f"Unsupported quoting or interpolation in {name}")
        lines.append(f"{name}='{value}'")
    # Exclusive creation preserves existing passwords and the API-key encryption key.
    descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        stream.write("\n".join(lines) + "\n")
    print("Cloud environment created. Secret values were not printed.")
    print("Model API key configured:", bool(values.get("DASHSCOPE_API_KEY")))


def openssl_output(arguments, failure, input_data=None):
    try:
        return subprocess.run(
            ["openssl", *arguments], input=input_data, capture_output=True,
            check=True, timeout=15,
        ).stdout
    except FileNotFoundError:
        raise ValueError("TLS validation requires the system openssl executable") from None
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        # Never print command output, certificate contents or private-key errors.
        raise ValueError(f"TLS validation failed: {failure}") from None


def validate_certificate(directory, hostname):
    certificate = str(directory / "fullchain.pem")
    private_key = str(directory / "privkey.pem")
    openssl_output(
        ["x509", "-in", certificate, "-noout", "-checkend", "0"],
        "certificate is invalid or expired",
    )
    # OpenSSL uses its configured system trust store; the supplied fullchain is
    # untrusted chain material, never a replacement trust anchor.
    openssl_output(
        ["verify", "-purpose", "sslserver", "-verify_hostname", hostname,
         "-untrusted", certificate, certificate],
        "certificate hostname, validity or trusted chain could not be verified",
    )
    certificate_public_key = openssl_output(
        ["x509", "-in", certificate, "-noout", "-pubkey"],
        "certificate public key could not be read",
    )
    certificate_public_key = openssl_output(
        ["pkey", "-pubin", "-outform", "DER"],
        "certificate public key is invalid", certificate_public_key,
    )
    key_public_key = openssl_output(
        ["pkey", "-in", private_key, "-passin", "pass:", "-pubout", "-outform", "DER"],
        "private key is invalid, encrypted or unreadable",
    )
    if certificate_public_key != key_public_key:
        raise ValueError("TLS validation failed: certificate and private key do not match")


def check_https_origin(path):
    origin = urlsplit(read_values(path)["PUBLIC_ORIGIN"])
    context = ssl.create_default_context()
    for request_path in ("/", "/api/v1/auth/csrf"):
        connection = http.client.HTTPSConnection(origin.hostname, origin.port or 443, timeout=15)
        try:
            # Connect locally while retaining the real hostname for SNI, hostname
            # verification and the HTTP Host header. No DNS or proxy is involved.
            transport = socket.create_connection(("127.0.0.1", origin.port or 443), timeout=15)
            try:
                connection.sock = context.wrap_socket(transport, server_hostname=origin.hostname)
            except OSError:
                transport.close()
                raise
            connection.request("GET", request_path, headers={"Host": origin.netloc})
            response = connection.getresponse()
            body = response.read(1024 * 1024 + 1)
            if response.status != 200 or len(body) > 1024 * 1024:
                raise ValueError("unexpected HTTPS response")
            if request_path.endswith("/csrf"):
                payload = json.loads(body)
                data = payload.get("data") if isinstance(payload, dict) else None
                if (not isinstance(data, dict) or not isinstance(data.get("token"), str)
                        or not data["token"] or data.get("headerName") != "X-XSRF-TOKEN"):
                    raise ValueError("unexpected CSRF response")
        except (OSError, http.client.HTTPException, ValueError):
            raise ValueError(f"Trusted HTTPS health check failed for {request_path}") from None
        finally:
            connection.close()
    print("Trusted HTTPS root and CSRF health checks passed.")


def validate_environment(path, tls=False):
    values = read_values(path)
    for name in ("POSTGRES_PASSWORD", "MINIO_ROOT_PASSWORD"):
        value = values.get(name, "")
        if len(value) < 16 or value in {"fashion_2404", "change-me", "REPLACE_ME"}:
            raise ValueError(f"{name} requires a unique password of at least 16 characters")
    try:
        encryption_key = base64.b64decode(values.get("AI_SETTINGS_ENCRYPTION_KEY", ""), validate=True)
    except ValueError:
        raise ValueError("AI_SETTINGS_ENCRYPTION_KEY must be 32 random bytes in Base64") from None
    if len(encryption_key) != 32:
        raise ValueError("AI_SETTINGS_ENCRYPTION_KEY must be 32 random bytes in Base64")
    origin = urlsplit(values.get("PUBLIC_ORIGIN", "http://localhost:8090"))
    if (origin.scheme not in {"http", "https"} or not origin.hostname or origin.username
            or origin.password or origin.path or origin.query or origin.fragment):
        raise ValueError("PUBLIC_ORIGIN must be an exact HTTP(S) origin without a path")
    if tls:
        if origin.scheme != "https":
            raise ValueError("The TLS deployment requires an HTTPS PUBLIC_ORIGIN")
        if (origin.port or 443) != int(values.get("FRONTEND_TLS_PORT", "8443")):
            raise ValueError("The HTTPS origin port must match FRONTEND_TLS_PORT")
        certificate_directory = Path(values.get("TLS_CERT_DIR", ""))
        if not certificate_directory.is_absolute() or not all(
                (certificate_directory / name).is_file() for name in ("fullchain.pem", "privkey.pem")):
            raise ValueError("TLS_CERT_DIR requires an absolute directory with fullchain.pem and privkey.pem")
        validate_certificate(certificate_directory, origin.hostname)
    print("Cloud environment validation passed; secret values were not printed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--template", type=Path, default=Path(".env.cloud.example"))
    parser.add_argument("--output", type=Path, default=Path(".env.cloud"))
    parser.add_argument("--model-env-file", type=Path,
                        help="Optionally import only model API settings from an existing local env file")
    parser.add_argument("--check", action="store_true", help="Validate an existing environment without changing it")
    parser.add_argument("--tls", action="store_true", help="Also validate HTTPS origin and certificate files")
    parser.add_argument("--check-https", action="store_true",
                        help="With --check, verify HTTPS root and CSRF over loopback with the real hostname")
    args = parser.parse_args()
    if args.check_https and not args.check:
        parser.error("--check-https requires --check")
    try:
        if args.check:
            validate_environment(args.output, args.tls or args.check_https)
            if args.check_https:
                check_https_origin(args.output)
        else:
            create_environment(args.template, args.output, args.model_env_file)
    except FileExistsError:
        parser.exit(1, "Environment already exists; existing secrets were preserved.\n")
    except ValueError as error:
        parser.exit(1, f"Environment validation failed: {error}\n")
    except OSError:
        parser.exit(1, "Environment creation failed. Check paths and single-line literal values; secrets were not printed.\n")


if __name__ == "__main__":
    main()
