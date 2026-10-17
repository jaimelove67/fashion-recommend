#!/usr/bin/env bash
# Run from the extracted release directory. Does not install Docker or open ports.
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
mode="${1:-http}"
build_option="${2:-build}"
if [[ ( "$mode" != http && "$mode" != tls ) || ( "$build_option" != build && "$build_option" != --no-build ) ]]; then
    printf 'Usage: bash scripts/cloud-deploy.sh [http|tls] [--no-build]\n' >&2
    exit 2
fi
if [[ ! -f .env.cloud ]]; then
    printf 'Create .env.cloud with scripts/cloud-init-env.py before deployment.\n' >&2
    exit 1
fi
chmod 600 .env.cloud
validation=(python3 scripts/cloud-init-env.py --check)
if [[ "$mode" == tls ]]; then
    validation+=(--tls)
fi
"${validation[@]}"
docker compose version >/dev/null
docker info >/dev/null
compose=(docker compose --env-file .env.cloud -f docker-compose.yml -f docker-compose.cloud.yml)
if [[ "$mode" == tls ]]; then
    compose+=(-f docker-compose.cloud.tls.yml)
fi
compose+=(--profile app)
"${compose[@]}" config --quiet

# Resource caps apply at runtime. Sequential builds reduce build-time memory peaks.
if [[ "$build_option" != --no-build ]]; then
    for service in backend frontend trend-collector; do
        "${compose[@]}" build "$service"
    done
fi
if [[ "$mode" == tls ]]; then
    # Check the mounted certificate/config before replacing the working frontend.
    # A synthetic backend entry is sufficient for nginx -t and avoids starting
    # dependencies or requiring a running backend's Docker DNS record.
    "${compose[@]}" run --rm --no-deps --pull never --entrypoint sh frontend -c \
        'printf "\n127.0.0.1 backend\n" >> /etc/hosts; exec nginx -t'
fi
startup_options=(-d --no-build --wait --wait-timeout 300)
if [[ "$build_option" == --no-build ]]; then
    startup_options+=(--pull never)
fi
"${compose[@]}" up "${startup_options[@]}"
"${compose[@]}" ps
# A recreated backend can receive a new container IP. Refresh Nginx's DNS lookup.
"${compose[@]}" exec -T frontend nginx -t
"${compose[@]}" exec -T frontend nginx -s reload

if [[ "$mode" == http ]]; then
    "${compose[@]}" exec -T frontend wget -q -O /dev/null http://127.0.0.1/
    "${compose[@]}" exec -T frontend wget -q -O /dev/null http://127.0.0.1/api/v1/auth/csrf
else
    python3 scripts/cloud-init-env.py --check --tls --check-https
fi
"${compose[@]}" exec -T backend wget -q -O - http://127.0.0.1:8080/actuator/health
printf '\nDeployment health checks passed.\n'
