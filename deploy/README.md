# Production deployment

The test deployment runs at `https://match.soyorin.love` on `8.153.153.41`.
It reuses the 1Panel PostgreSQL, Redis, RustFS, and OpenResty services already
installed on that host. The API runs as the `knowledge-match.service` systemd
unit, and OpenResty serves the built frontend directly.

Secrets live only in `/opt/challenging-knowledge-match/deploy/.env` on the
server. The file must remain owned by `root:knowledge-match` with mode `0640`.

To update the deployment after changes reach `main`:

```bash
cd /opt/challenging-knowledge-match
git pull --ff-only origin main
npm ci
npm run build
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -f server/pom.xml test package
cp -a dist/. /opt/1panel/www/sites/match.soyorin.love/index/
systemctl restart knowledge-match.service
curl -fsS https://match.soyorin.love/api/health
```

The wildcard certificate is managed by `acme.sh`. It installs renewed files
under `/opt/1panel/www/sites/match.soyorin.love/ssl/` and reloads OpenResty
after a successful renewal.
