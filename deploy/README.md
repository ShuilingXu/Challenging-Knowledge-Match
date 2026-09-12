# Production deployment

The deployment runs at `https://match.soyorin.love` on `sh-1.soyorin.love`.
It reuses the 1Panel PostgreSQL, Redis, and OpenResty services installed on that
host and runs a dedicated `knowledge-match-minio` container for media. The API
runs as the `knowledge-match.service` systemd unit on loopback port `8082`;
MinIO uses loopback port `9000`, and OpenResty serves the built frontend.

Secrets live only in `/opt/challenging-knowledge-match/deploy/.env` on the
server. The file must remain owned by `root:knowledge-match` with mode `0640`.
The generated initial administrator login is stored, root-only, in
`/root/knowledge-match-credentials.txt`.

To update the deployment from a checked-out workstation:

```bash
npm ci
npm run build
mvn -f server/pom.xml test package
scp -i ~/.ssh/prod-env server/target/knowledge-match-api-0.1.0.jar \
  root@sh-1.soyorin.love:/opt/challenging-knowledge-match/server/target/knowledge-match-api-0.1.0.jar.new
scp -i ~/.ssh/prod-env -r dist/. \
  root@sh-1.soyorin.love:/opt/challenging-knowledge-match/staging-dist/
scp -i ~/.ssh/prod-env deploy/{run-api.sh,configure-existing-services.sh,knowledge-match.service,match.sh-1.soyorin.love.conf,match-http-bootstrap.conf,renew-match-certificate.sh,bootstrap-sh-1.soyorin.love.sh} \
  root@sh-1.soyorin.love:/opt/challenging-knowledge-match/deploy/
ssh -i ~/.ssh/prod-env root@sh-1.soyorin.love \
  'bash /opt/challenging-knowledge-match/deploy/bootstrap-sh-1.soyorin.love.sh'
curl -fsS https://match.soyorin.love/api/health
```

The site certificate is managed by Certbot. Its deploy hook installs renewed
files under `/opt/1panel/www/sites/match.soyorin.love/ssl/` and reloads
OpenResty after a successful renewal.
