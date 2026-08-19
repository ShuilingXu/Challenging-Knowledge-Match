#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR=/opt/challenging-knowledge-match/deploy
set -a
# shellcheck disable=SC1091
source "$DEPLOY_DIR/.env"
set +a

APP_URL="https://${APP_DOMAIN}"
LOCAL_APP=(--resolve "${APP_DOMAIN}:443:127.0.0.1")
LOCAL_S3=(--resolve "${S3_DOMAIN}:443:127.0.0.1")

login_payload=$(APP_PASSWORD="$APP_BOOTSTRAP_PASSWORD" python3 -c \
  'import json, os; print(json.dumps({"username": "sysadmin", "password": os.environ["APP_PASSWORD"]}))')
login_response=$(curl -fsS "${LOCAL_APP[@]}" -H 'Content-Type: application/json' \
  --data "$login_payload" "$APP_URL/api/auth/login")
access_token=$(printf '%s' "$login_response" | python3 -c \
  'import json, sys; print(json.load(sys.stdin)["accessToken"])')

settings_payload=$(python3 -c 'import json; print(json.dumps({
  "domain": "match.soyorin.love",
  "storageEnabled": True,
  "storageEndpoint": "https://match-s3.soyorin.love",
  "storageRegion": "us-east-1",
  "storageBucket": "matrixlive-media",
  "storageAccessKey": "rustfsadmin",
  "storageSecretKey": "rustfsadmin",
  "storagePublicBaseUrl": "",
  "storageAddressingStyle": "PATH"
}))')
settings_response=$(curl -fsS "${LOCAL_APP[@]}" -X PATCH \
  -H "Authorization: Bearer $access_token" -H 'Content-Type: application/json' \
  --data "$settings_payload" "$APP_URL/api/admin/site-settings")
printf '%s' "$settings_response" | python3 -c '
import json, sys
s = json.load(sys.stdin)
assert s["domain"] == "match.soyorin.love"
assert s["storageEnabled"] is True
assert s["storageEndpoint"] == "https://match-s3.soyorin.love"
assert s["storageBucket"] == "matrixlive-media"
assert s["storageAccessKey"] == "rustfsadmin"
assert s["storageSecretConfigured"] is True
assert s["storageAddressingStyle"] == "PATH"
'

activities=$(curl -fsS "${LOCAL_APP[@]}" "$APP_URL/api/activities")
activity_id=$(printf '%s' "$activities" | python3 -c \
  'import json, sys; rows=json.load(sys.stdin); assert rows; print(rows[0]["id"])')

media_file=$(mktemp --suffix=.svg)
trap 'rm -f "$media_file"' EXIT
printf '%s' '<svg xmlns="http://www.w3.org/2000/svg" width="2" height="2"><rect width="2" height="2" fill="#009688"/></svg>' > "$media_file"
upload_response=$(curl -fsS "${LOCAL_APP[@]}" \
  -H "Authorization: Bearer $access_token" \
  -F 'category=deployment-smoke' -F "file=@${media_file};type=image/svg+xml" \
  "$APP_URL/api/activities/$activity_id/media")
object_key=$(printf '%s' "$upload_response" | python3 -c \
  'import json, sys; print(json.load(sys.stdin)["objectKey"])')
media_url=$(printf '%s' "$upload_response" | python3 -c \
  'import json, sys; print(json.load(sys.stdin)["url"])')

curl -fsS "${LOCAL_S3[@]}" "$media_url" -o /dev/null

docker run --rm --network 1panel-network --entrypoint /bin/sh minio/mc:latest -c \
  "mc alias set rustfs http://1Panel-rustfs-EBUK:9000 '${S3_ACCESS_KEY}' '${S3_SECRET_KEY}' >/dev/null &&
   mc stat 'rustfs/${S3_BUCKET}/${object_key}' >/dev/null &&
   mc rm 'rustfs/${S3_BUCKET}/${object_key}' >/dev/null"

echo "settings=ok"
echo "login=ok"
echo "activity=$activity_id"
echo "rustfs_upload=ok"
echo "rustfs_presigned_download=ok"
echo "rustfs_smoke_object_removed=ok"
