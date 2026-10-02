#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/challenging-knowledge-match
DEPLOY_DIR="$APP_DIR/deploy"
SITE_DIR=/opt/1panel/www/sites/match.soyorin.love
SITE_INDEX="$SITE_DIR/index"
OPENRESTY_CONF_DIR=/opt/1panel/www/conf.d
OPENRESTY_CONTAINER=1Panel-openresty-0IEB
MINIO_CONTAINER=knowledge-match-minio

if [[ "$APP_DIR" != /opt/challenging-knowledge-match ]] ||
   [[ "$SITE_INDEX" != /opt/1panel/www/sites/match.soyorin.love/index ]]; then
  echo "Refusing to deploy to an unexpected path" >&2
  exit 1
fi

install -d -m 755 "$APP_DIR/server/target" "$DEPLOY_DIR" "$SITE_INDEX" \
  "$SITE_DIR/log" "$SITE_DIR/ssl" "$OPENRESTY_CONF_DIR"

for script in "$DEPLOY_DIR"/*.sh; do
  sed -i 's/\r$//' "$script"
  chmod 750 "$script"
done

if [[ ! -s "$DEPLOY_DIR/.env" ]]; then
  umask 077
  database_password=$(openssl rand -hex 24)
  storage_secret=$(openssl rand -hex 24)
  jwt_secret=$(openssl rand -base64 48 | tr -d '\n')
  bootstrap_password=$(openssl rand -base64 24 | tr '/+' '_-' | tr -d '=\n')
  cat > "$DEPLOY_DIR/.env" <<ENV
APP_DOMAIN=match.soyorin.love
POSTGRES_DB=knowledge_match
POSTGRES_USER=knowledge_match
POSTGRES_PASSWORD=$database_password
REDIS_PASSWORD=populated-by-configure-existing-services
S3_ENABLED=true
S3_ENDPOINT=https://match.soyorin.love
S3_PUBLIC_BASE_URL=
S3_REGION=us-east-1
S3_BUCKET=matrixlive-media
S3_ACCESS_KEY=matchminio
S3_SECRET_KEY=$storage_secret
JWT_SECRET=$jwt_secret
JWT_ISSUER=knowledge-match
APP_BOOTSTRAP_PASSWORD=$bootstrap_password
APP_BACKEND_PORT=8082
ENV
  cat > /root/knowledge-match-credentials.txt <<CREDS
URL=https://match.soyorin.love
USERNAME=sysadmin
PASSWORD=$bootstrap_password
CREDS
  chmod 600 /root/knowledge-match-credentials.txt
fi

"$DEPLOY_DIR/configure-existing-services.sh"
chown root:knowledge-match "$DEPLOY_DIR/run-api.sh"
chmod 750 "$DEPLOY_DIR/run-api.sh"

set -a
# shellcheck disable=SC1091
source "$DEPLOY_DIR/.env"
set +a

install -d -m 700 "$APP_DIR/minio-data"
if ! docker container inspect "$MINIO_CONTAINER" >/dev/null 2>&1; then
  docker run -d --name "$MINIO_CONTAINER" --restart unless-stopped \
    -p 127.0.0.1:9000:9000 \
    -v "$APP_DIR/minio-data:/data" \
    -e "MINIO_ROOT_USER=$S3_ACCESS_KEY" \
    -e "MINIO_ROOT_PASSWORD=$S3_SECRET_KEY" \
    minio/minio:latest server /data >/dev/null
else
  docker start "$MINIO_CONTAINER" >/dev/null
fi

for attempt in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:9000/minio/health/live >/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 30 ]]; then
    docker logs --tail 100 "$MINIO_CONTAINER" >&2
    exit 1
  fi
  sleep 2
done

if [[ -f "$APP_DIR/server/target/knowledge-match-api-0.1.0.jar.new" ]]; then
  install -o root -g knowledge-match -m 640 \
    "$APP_DIR/server/target/knowledge-match-api-0.1.0.jar.new" \
    "$APP_DIR/server/target/knowledge-match-api-0.1.0.jar"
  rm -f "$APP_DIR/server/target/knowledge-match-api-0.1.0.jar.new"
elif [[ ! -f "$APP_DIR/server/target/knowledge-match-api-0.1.0.jar" ]]; then
  echo "Backend artifact is missing" >&2
  exit 1
fi

find "$SITE_INDEX" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
cp -a "$APP_DIR/staging-dist/." "$SITE_INDEX/"
find "$SITE_INDEX" -type d -exec chmod 755 {} +
find "$SITE_INDEX" -type f -exec chmod 644 {} +

install -o root -g root -m 644 "$DEPLOY_DIR/knowledge-match.service" \
  /etc/systemd/system/knowledge-match.service
systemctl daemon-reload
systemctl enable --now knowledge-match.service

for attempt in $(seq 1 45); do
  if curl -fsS http://127.0.0.1:8082/api/health >/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 45 ]]; then
    journalctl -u knowledge-match.service -n 150 --no-pager >&2
    exit 1
  fi
  sleep 2
done

install -o root -g root -m 644 "$DEPLOY_DIR/match-http-bootstrap.conf" \
  "$OPENRESTY_CONF_DIR/match.soyorin.love.conf"
docker exec "$OPENRESTY_CONTAINER" openresty -t
docker exec "$OPENRESTY_CONTAINER" openresty -s reload

install -d -o root -g root -m 755 /etc/letsencrypt/renewal-hooks/deploy
install -o root -g root -m 750 "$DEPLOY_DIR/renew-match-certificate.sh" \
  /etc/letsencrypt/renewal-hooks/deploy/knowledge-match-openresty
certbot certonly --webroot --webroot-path "$SITE_INDEX" \
  --domain match.soyorin.love --non-interactive --agree-tos \
  --register-unsafely-without-email --keep-until-expiring
/etc/letsencrypt/renewal-hooks/deploy/knowledge-match-openresty

install -o root -g root -m 644 "$DEPLOY_DIR/match.sh-1.soyorin.love.conf" \
  "$OPENRESTY_CONF_DIR/match.soyorin.love.conf"
docker exec "$OPENRESTY_CONTAINER" openresty -t
docker exec "$OPENRESTY_CONTAINER" openresty -s reload

curl -fsS https://match.soyorin.love/api/health
echo
echo "Knowledge Match deployment completed"
