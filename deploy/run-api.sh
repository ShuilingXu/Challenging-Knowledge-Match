#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR=/opt/challenging-knowledge-match/deploy
APP_DIR=/opt/challenging-knowledge-match

set -a
# shellcheck disable=SC1091
source "$DEPLOY_DIR/.env"
set +a

export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/${POSTGRES_DB}"
export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
export REDIS_URL="redis://:${REDIS_PASSWORD}@127.0.0.1:6379"
export REALTIME_REDIS_ENABLED=true
export MANAGEMENT_HEALTH_REDIS_ENABLED=true
export S3_ENABLED=true
export S3_ENDPOINT="https://${S3_DOMAIN}"
export S3_ADDRESSING_STYLE=PATH
export JWT_REFRESH_COOKIE_SECURE=true

exec /usr/lib/jvm/java-21-openjdk-amd64/bin/java \
  -jar "$APP_DIR/server/target/knowledge-match-api-0.1.0.jar" \
  --spring.profiles.active=compose \
  --server.address=127.0.0.1
