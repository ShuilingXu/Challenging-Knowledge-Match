#!/usr/bin/env bash
set -euo pipefail
DEPLOY_DIR=${DEPLOY_DIR:-/opt/challenging-knowledge-match/deploy}
set -a
source "$DEPLOY_DIR/.env"
set +a
APP_URL="https://${APP_DOMAIN}"
curl -fsS "$APP_URL/api/health" | python3 -c 'import json,sys;assert json.load(sys.stdin)["status"]=="UP"'
curl -fsS "$APP_URL/" | grep -q '<div id="root"'
systemctl is-active --quiet knowledge-match.service
docker inspect -f '{{.State.Running}}' knowledge-match-minio | grep -qx true
curl -fsS http://127.0.0.1:9000/minio/health/live >/dev/null
test "$(curl -sS -o /dev/null -w '%{http_code}' "http://${APP_DOMAIN}/")" = 301
openssl s_client -connect "${APP_DOMAIN}:443" -servername "$APP_DOMAIN" -verify_hostname "$APP_DOMAIN" -verify_return_error </dev/null 2>/dev/null | grep -q 'Verify return code: 0'
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "${POSTGRES_CONTAINER:-1Panel-postgresql-DFc5}" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -tAc 'select 1' | grep -qx 1
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "${REDIS_CONTAINER:-1Panel-redis-yTI2}" redis-cli -h 127.0.0.1 ping | grep -qx PONG
bash "$DEPLOY_DIR/smoke-production.sh"
echo "health, frontend, database, Redis, TLS and storage: ok"
