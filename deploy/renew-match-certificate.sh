#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR=/etc/letsencrypt/live/match.soyorin.love
TARGET_DIR=/opt/1panel/www/sites/match.soyorin.love/ssl

install -d -m 755 "$TARGET_DIR"
install -m 644 "$SOURCE_DIR/fullchain.pem" "$TARGET_DIR/fullchain.pem"
install -m 600 "$SOURCE_DIR/privkey.pem" "$TARGET_DIR/privkey.pem"
docker exec 1Panel-openresty-0IEB openresty -t
docker exec 1Panel-openresty-0IEB openresty -s reload
