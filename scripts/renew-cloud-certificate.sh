#!/usr/bin/env bash
# A dedicated systemd timer runs this check; acme.sh owns renewal scheduling.
set -euo pipefail
umask 077

exec /opt/zhiji/shared/acme/client/acme.sh --cron \
    --home /opt/zhiji/shared/acme/client \
    --config-home /opt/zhiji/shared/acme/production \
    --cert-home /opt/zhiji/shared/acme/certificates \
    --server letsencrypt --no-color
