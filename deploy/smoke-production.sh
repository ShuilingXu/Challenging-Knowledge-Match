#!/usr/bin/env bash
set -euo pipefail
DEPLOY_DIR=${DEPLOY_DIR:-/opt/challenging-knowledge-match/deploy}
set -a
source "$DEPLOY_DIR/.env"
set +a
APP_URL="https://${APP_DOMAIN}"
LOCAL_APP=(--resolve "${APP_DOMAIN}:443:127.0.0.1")
work=$(mktemp -d)
object_key=
cleanup() {
  local result=$?
  if [[ -n "$object_key" ]]; then
    docker run --rm --network host \
      -e S3_ACCESS_KEY -e S3_SECRET_KEY -e S3_BUCKET -e OBJECT_KEY="$object_key" \
      --entrypoint /bin/sh minio/mc:latest -c \
      'mc alias set smoke http://127.0.0.1:9000 "$S3_ACCESS_KEY" "$S3_SECRET_KEY" >/dev/null && mc rm "smoke/$S3_BUCKET/$OBJECT_KEY" >/dev/null' || { echo "Smoke object cleanup failed" >&2; result=1; }
  fi
  rm -rf -- "$work"
  exit "$result"
}
trap cleanup EXIT
login_payload=$(python3 -c 'import json,os; print(json.dumps({"username":"sysadmin","password":os.environ["APP_BOOTSTRAP_PASSWORD"]}))')
login_response=$(curl -fsS "${LOCAL_APP[@]}" -H 'Content-Type: application/json' --data "$login_payload" "$APP_URL/api/auth/login")
access_token=$(printf '%s' "$login_response" | python3 -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])')
curl -fsS "${LOCAL_APP[@]}" -H "Authorization: Bearer $access_token" "$APP_URL/api/admin/site-settings" | python3 -c 'import json,sys;s=json.load(sys.stdin);assert s["storageEnabled"] and s["storageSecretConfigured"]'
activity_id=$(curl -fsS "${LOCAL_APP[@]}" -H "Authorization: Bearer $access_token" "$APP_URL/api/activities" | python3 -c 'import json,sys;rows=json.load(sys.stdin);assert rows;print(rows[0]["id"])')
python3 -c 'import base64,sys;open(sys.argv[1],"wb").write(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII="))' "$work/media.png"
upload=$(curl -fsS "${LOCAL_APP[@]}" -H "Authorization: Bearer $access_token" -F category=deployment-smoke -F "file=@$work/media.png;type=image/png" "$APP_URL/api/activities/$activity_id/media")
object_key=$(printf '%s' "$upload" | python3 -c 'import json,sys;print(json.load(sys.stdin)["objectKey"])')
media_path=$(printf '%s' "$upload" | python3 -c 'import json,sys;print(json.load(sys.stdin)["url"])')
curl -fsS "${LOCAL_APP[@]}" "$APP_URL$media_path" -o "$work/download.png"
cmp "$work/media.png" "$work/download.png"
echo "login, storage configuration, upload and download: ok"
