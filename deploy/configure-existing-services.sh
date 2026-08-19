#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR=/opt/challenging-knowledge-match/deploy
ENV_FILE="$DEPLOY_DIR/.env"
POSTGRES_CONTAINER=1Panel-postgresql-DFc5
REDIS_CONTAINER=1Panel-redis-yTI2

app_db_password=$(sed -n 's/^POSTGRES_PASSWORD=//p' "$ENV_FILE")
if [[ ! "$app_db_password" =~ ^[a-f0-9]{32,}$ ]]; then
  echo "The generated application database password is missing or malformed" >&2
  exit 1
fi

redis_password=$(
  docker inspect "$REDIS_CONTAINER" |
    python3 -c 'import json, sys; c = json.load(sys.stdin)[0]["Config"]["Cmd"]; print(c[c.index("--requirepass") + 1])'
)
if [[ -z "$redis_password" || "$redis_password" == *$'\n'* ]]; then
  echo "Could not read the existing Redis password" >&2
  exit 1
fi

docker exec -i -e APP_DB_PASSWORD="$app_db_password" "$POSTGRES_CONTAINER" sh <<'CONTAINER_SCRIPT'
set -eu
if psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -tAc \
    "SELECT 1 FROM pg_roles WHERE rolname = 'knowledge_match'" | grep -q 1; then
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
    -c "ALTER ROLE knowledge_match WITH LOGIN PASSWORD '$APP_DB_PASSWORD'" >/dev/null
else
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
    -c "CREATE ROLE knowledge_match LOGIN PASSWORD '$APP_DB_PASSWORD'" >/dev/null
fi

if ! psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = 'knowledge_match'" | grep -q 1; then
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
    -c "CREATE DATABASE knowledge_match OWNER knowledge_match" >/dev/null
fi

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
  -c "ALTER DATABASE knowledge_match OWNER TO knowledge_match" >/dev/null
CONTAINER_SCRIPT

getent group knowledge-match >/dev/null || groupadd --system knowledge-match
id knowledge-match >/dev/null 2>&1 || useradd --system --gid knowledge-match \
  --home-dir /nonexistent --shell /usr/sbin/nologin knowledge-match

temporary_env=$(mktemp "$DEPLOY_DIR/.env.XXXXXX")
while IFS= read -r line || [[ -n "$line" ]]; do
  case "$line" in
    POSTGRES_DB=*) printf 'POSTGRES_DB=knowledge_match\n' ;;
    POSTGRES_USER=*) printf 'POSTGRES_USER=knowledge_match\n' ;;
    REDIS_PASSWORD=*) printf 'REDIS_PASSWORD=%s\n' "$redis_password" ;;
    *) printf '%s\n' "$line" ;;
  esac
done < "$ENV_FILE" > "$temporary_env"
install -o root -g knowledge-match -m 640 "$temporary_env" "$ENV_FILE"
rm -f "$temporary_env"

docker exec "$POSTGRES_CONTAINER" sh -c \
  'psql -U "$POSTGRES_USER" -d postgres -tAc "SELECT datname FROM pg_database WHERE datname = '\''knowledge_match'\''"' |
  grep -qx knowledge_match
docker exec "$REDIS_CONTAINER" redis-cli -a "$redis_password" --no-auth-warning ping |
  grep -qx PONG

echo "Existing PostgreSQL and Redis are ready for Knowledge Match"
