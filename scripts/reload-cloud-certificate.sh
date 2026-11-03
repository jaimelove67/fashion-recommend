#!/usr/bin/env bash
# Installed as acme.sh's deploy callback for the existing frontend container.
set -euo pipefail

certificate_dir="${TLS_CERT_DIR:-/etc/zhiji/tls}"
python3 - "$certificate_dir" <<'PY'
from pathlib import Path
import subprocess
import sys

directory = Path(sys.argv[1])
certificate = str(directory / 'fullchain.pem')
private_key = str(directory / 'privkey.pem')

def openssl(arguments, input_data=None):
    result = subprocess.run(['openssl', *arguments], input=input_data, capture_output=True)
    if result.returncode:
        raise SystemExit('Certificate verification failed; the current Nginx workers were not reloaded.')
    return result.stdout

openssl(['x509', '-in', certificate, '-noout', '-checkend', '0'])
for hostname in ('jaime.ltd', 'www.jaime.ltd'):
    openssl(['verify', '-purpose', 'sslserver', '-verify_hostname', hostname,
             '-untrusted', certificate, certificate])
public_key = openssl(['x509', '-in', certificate, '-noout', '-pubkey'])
public_key = openssl(['pkey', '-pubin', '-outform', 'DER'], public_key)
key_public_key = openssl(['pkey', '-in', private_key, '-passin', 'pass:', '-pubout', '-outform', 'DER'])
if public_key != key_public_key:
    raise SystemExit('Certificate/private-key mismatch; the current Nginx workers were not reloaded.')
print('Certificate chain, names, expiry and private-key match verified.')
PY

docker exec fashion-recommendation-cloud-frontend-1 nginx -t
docker exec fashion-recommendation-cloud-frontend-1 nginx -s reload
