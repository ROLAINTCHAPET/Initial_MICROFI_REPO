# MICROFI

MICROFI is a digital cash-collection network for microfinance institutions (MFIs) in the CEMAC
region. Field agents collect cash from clients on a phone — online or offline — a back-office
reconciles it at end of day (OFJ), and a middleware posts the result into the institution's Core
Banking System (CBS).

## Architecture

Four deployables plus a gateway, running behind Kong:

| Path | Stack | Port | Role |
|---|---|---|---|
| `microfi-core/` | Spring Boot 4, Java 21, WebFlux + JPA | 8080 | All business logic (modular monolith) |
| `microfi-middleware/` | Spring Boot 4, Java 21, JPA | 8081 | CBS vendor adapters; called by Core only |
| `microfi-backoffice/` | Next.js 16 (React 19), Tailwind v4 | 3000 | Admin / branch manager / cashier web UI |
| `microfi-mobile/` | Flutter 3 (Dart) | — | Agent app + client digital booklet (one codebase) |
| `kong/` | Kong 3.7, DB-less | 8000/8443 | TLS, JWT validation, rate limiting, routing |
| `prometheus/ loki/ promtail/ tempo/ grafana/` | Grafana LGTM stack | 9090 / 3100 / 3200 / 3001 | Metrics, logs, traces, dashboards |

Both the mobile app and the back-office talk to Kong, never to Core directly, so
authentication, rate-limiting and CORS are always exercised the same way in every environment.

## Getting started

Bring up the full backend stack (Postgres, Redis, RabbitMQ, Core, Middleware, Kong, and
observability) with Docker Compose; the back-office and mobile app run outside it during
development:

```bash
docker compose up --build
```

Once it's up (check `docker compose ps` for the actual port mapping — hosts are deliberately
offset to avoid clashing with other local stacks):

- Back-office UI: `:3000`
- API gateway (Kong): `:8000`
- Grafana: `:3300` (admin/admin)
- RabbitMQ management: `:25672`

### Core / Middleware

```bash
cd microfi-core && ./mvnw spring-boot:run
cd microfi-core && ./mvnw test
```

### Back-office

Needs `MICROFI_API_BASE_URL` (defaults to `http://localhost:8000/api/v1`, i.e. Kong):

```bash
cd microfi-backoffice && npm run dev
```

### Mobile

Requires Flutter ≥ 3.44:

```bash
cd microfi-mobile && flutter pub get && flutter run
cd microfi-mobile && flutter test
```

## Key concepts

- **Offline-first collection**: agents can record cash collections without connectivity; the
  queue syncs and reconciles once back online.
- **Offline Field Collection Security Algorithm**: device/installation binding, a signed
  hash-chain on every collection, and an admin-only reset flow prevent an agent from
  uninstalling the app to evade the offline ceiling/geofence/schedule checks.
- **End-of-day reconciliation (OFJ)**: a cashier's physical count is matched against the
  digital total collected by each agent before anything posts to the CBS.
- **No migration tool**: schema changes ship as entity changes (`ddl-auto=update`).

## Documentation

Project-specific conventions, module boundaries, and the full request-path/JWT contract are
documented in `CLAUDE.md` at the repo root. Functional/business requirements and use cases live
under `Documentation/`.

## Testing

- `microfi-core`: JUnit 5 + Mockito (services), `@WebFluxTest` (controllers).
- `microfi-mobile`: unit/widget tests in `test/`, device tests in `integration_test/`.
- `microfi-backoffice`: `npm run lint`.
