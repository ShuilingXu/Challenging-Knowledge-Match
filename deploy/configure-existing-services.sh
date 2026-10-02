#!/usr/bin/env bash
set -euo pipefail
DEPLOY_DIR=${DEPLOY_DIR:-/opt/challenging-knowledge-match/deploy}
ENV_FILE="$DEPLOY_DIR/.env"
POSTGRES_CONTAINER=${POSTGRES_CONTAINER:-1Panel-postgresql-DFc5}
REDIS_CONTAINER=${REDIS_CONTAINER:-1Panel-redis-yTI2}
set -a
source "$ENV_FILE"
set +a
if [[ -z "${REDIS_PASSWORD:-}" || "$REDIS_PASSWORD" == populated-* ]]; then
  export REDIS_PASSWORD
  REDIS_PASSWORD=$(docker inspect "$REDIS_CONTAINER" | python3 -c 'import json,sys; c=json.load(sys.stdin)[0]["Config"]["Cmd"]; print(c[c.index("--requirepass")+1])')
  [[ -n "$REDIS_PASSWORD" && "$REDIS_PASSWORD" != *$'\n'* ]] || { echo "Cannot read existing Redis password" >&2; exit 1; }
  python3 - "$ENV_FILE" <<'PY'
import os, pathlib, shlex, sys
p=pathlib.Path(sys.argv[1])
rows=p.read_text().splitlines()
replacement="REDIS_PASSWORD="+shlex.quote(os.environ["REDIS_PASSWORD"])
p.write_text("\n".join(replacement if row.startswith("REDIS_PASSWORD=") else row for row in rows)+"\n")
PY
fi
[[ "$POSTGRES_DB" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ && "$POSTGRES_USER" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]] || { echo "Invalid database/user name" >&2; exit 1; }
[[ ${#POSTGRES_PASSWORD} -ge 16 && "$POSTGRES_PASSWORD" != replace* ]] || { echo "Set POSTGRES_PASSWORD to a real secret of at least 16 characters" >&2; exit 1; }
docker exec -i -e APP_DB_NAME="$POSTGRES_DB" -e APP_DB_USER="$POSTGRES_USER" -e APP_DB_PASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" sh <<'CONTAINER_SCRIPT'
set -eu
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -v db="$APP_DB_NAME" -v app_user="$APP_DB_USER" -v password="$APP_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'app_user') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname=:'app_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'app_user', :'password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'app_user') WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname=:'db') \gexec
SQL
CONTAINER_SCRIPT
getent group knowledge-match >/dev/null || groupadd --system knowledge-match
id knowledge-match >/dev/null 2>&1 || useradd --system --gid knowledge-match --home-dir /nonexistent --shell /usr/sbin/nologin knowledge-match
chown root:knowledge-match "$ENV_FILE"
chmod 640 "$ENV_FILE"
