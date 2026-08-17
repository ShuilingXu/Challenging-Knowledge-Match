# Local runbook

## Fast UI preview

Run `npm install` once, then `npm run dev`. The UI is available at `http://127.0.0.1:4173`.

## API development

Run `mvn spring-boot:run` from `server`. It starts with an in-memory H2 database and seed data at `http://127.0.0.1:8080`.

- `POST /api/auth/login` returns a 15-minute access token and an HttpOnly, rotating refresh-token cookie.
- `POST /api/auth/refresh` rotates that cookie; `POST /api/auth/logout` revokes both the active access token and refresh token.
- `POST /api/auth/participant-token` issues an activity-scoped participant token after registration.
- The development accounts are `sysadmin`, `activity-admin`, and `event-staff`; all use `ChangeMe!2026` unless `APP_BOOTSTRAP_PASSWORD` is set before the first startup.
- `SYSTEM_ADMIN` has global access. `ACTIVITY_ADMIN` and `STAFF` are assigned per activity. Participants and paired screens receive activity-scoped tokens.
- STOMP clients connect to `/ws` and send `Authorization: Bearer <access token>` in the CONNECT frame. Subscriptions are restricted to a user's activity membership or to the paired screen device's own topic.

- `GET /api/health`
- `GET /api/activities`
- `POST /api/activities/{activityId}/venues/{venue}/registrations`
- `GET /api/activities/{activityId}/questions`
- `POST /api/activities/{activityId}/answers`
- `POST /api/activities/{activityId}/control`
- `GET /api/activities/{activityId}/scoreboard`
- `POST /api/activities/{activityId}/draws`
- `POST /api/activities/{activityId}/awards/{awardId}/redeem`

The WebSocket endpoint is `/ws`. Activity messages are broadcast to `/topic/activities/{activityId}` with an event type, payload, and timestamp.

## S3 media storage

Question media uploads use `POST /api/activities/{activityId}/media` with a
`multipart/form-data` part named `file`. The endpoint accepts image, audio, and
video MIME types and stores objects under `{activityId}/questions/`.

For AWS S3, set `S3_ENABLED=true`, `S3_REGION`, `S3_BUCKET`,
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` (or the `S3_ACCESS_KEY`/
`S3_SECRET_KEY` aliases) and leave `S3_ENDPOINT` empty. The API derives the
regional AWS endpoint. MinIO and other S3-compatible services should set
`S3_ENDPOINT`; `S3_ADDRESSING_STYLE` accepts `AUTO`, `PATH`, or `VIRTUAL`.
`S3_PUBLIC_BASE_URL` is optional; if it already ends with the bucket name, the
API appends only the object key, otherwise it appends `/{bucket}/{objectKey}`.

## Full local stack

Run `docker compose up --build`. The web app is served on `http://127.0.0.1:4173`, MinIO on ports `9000` and `9001`, and the API is proxied under `/api`.

The Compose profile uses PostgreSQL. Passwords in `docker-compose.yml` are development-only values; provide non-default values through your deployment environment.

For production, set a unique base64-encoded 256-bit-or-longer `JWT_SECRET`, a stable `JWT_ISSUER`, `JWT_REFRESH_COOKIE_SECURE=true`, non-default database credentials, and `APP_BOOTSTRAP_PASSWORD` before the first deployment. Flyway owns the schema; do not use Hibernate DDL updates in deployment.
