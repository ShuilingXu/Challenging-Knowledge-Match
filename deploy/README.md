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

`JWT_SECRET` and `APP_BOOTSTRAP_PASSWORD` are required; there are no application
defaults. Use a random Base64 secret of at least 32 decoded bytes and an initial
password of at least 12 characters. Changing the bootstrap variable does not
reset existing accounts. Supply real storage credentials from the server's
environment file, never the example placeholders.

Before upgrading, back up PostgreSQL. Flyway V15–V19 expand question fields,
add session/verification data, repair historical submission score differences,
and normalize this application's media URLs. Review previously issued prizes
against the corrected rankings; migrations do not revoke awards.
Existing participant and screen sessions need one new login/pairing to acquire
the new renewal credential. New sessions renew automatically. Pausing an
activity closes its answer window; staff must reopen a question after resuming.

Media stays in a private bucket and streams through same-origin API paths,
including byte-range playback. Anonymous bucket access and public presigned
URLs are unnecessary. Requests allow 24MB, with a default 20MB file limit.
Human verification uses one-use images and configurable five-minute limits:
`HUMAN_CHALLENGE_IP_LIMIT` and `HUMAN_PARTICIPANT_IP_LIMIT` default to 3000 for
shared venue networks; `HUMAN_CONTACT_LIMIT` defaults to 10. No MFA is added.

To update the deployment from a checked-out workstation:

```bash
npm ci
npm run build
mvn -f server/pom.xml test package
scp -i ~/.ssh/prod-env server/target/knowledge-match-api-0.1.0.jar \
  root@sh-1.soyorin.love:/opt/challenging-knowledge-match/server/target/knowledge-match-api-0.1.0.jar.new
scp -i ~/.ssh/prod-env -r dist/. \
  root@sh-1.soyorin.love:/opt/challenging-knowledge-match/staging-dist/
scp -i ~/.ssh/prod-env deploy/{run-api.sh,configure-existing-services.sh,knowledge-match.service,match.sh-1.soyorin.love.conf,match-http-bootstrap.conf,renew-match-certificate.sh,bootstrap-sh-1.soyorin.love.sh,verify-sh-1.soyorin.love.sh,smoke-production.sh} \
  root@sh-1.soyorin.love:/opt/challenging-knowledge-match/deploy/
ssh -i ~/.ssh/prod-env root@sh-1.soyorin.love \
  'bash /opt/challenging-knowledge-match/deploy/bootstrap-sh-1.soyorin.love.sh'
curl -fsS https://match.soyorin.love/api/health
```

Run `bash deploy/verify-sh-1.soyorin.love.sh` on the production host after updating.
It checks frontend, health, PostgreSQL, Redis, storage, TLS, and calls
`smoke-production.sh`. The smoke test reads storage settings without changing
them, uploads/downloads a temporary media object, and deletes it on exit.
Do not print the environment file or tokens into CI logs. The verification
scripts must be run against the real services; Compose parsing and local H2
tests alone do not establish production connectivity or concurrency capacity.

The site certificate is managed by Certbot. Its deploy hook installs renewed
files under `/opt/1panel/www/sites/match.soyorin.love/ssl/` and reloads
OpenResty after a successful renewal.
