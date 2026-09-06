# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

MICROFI is a digital cash-collection network for microfinance institutions in the CEMAC region:
field agents collect cash from clients on a phone (offline-capable), a back-office reconciles it at
end of day (OFJ), and a middleware posts the result into the institution's Core Banking System.

Four deployables plus a gateway:

| Path | Stack | Port | Role |
|---|---|---|---|
| `microfi-core/` | Spring Boot 4, Java 21, WebFlux + JPA | 8080 | All business logic (modular monolith) |
| `microfi-middleware/` | Spring Boot 4, Java 21, JPA | 8081 | CBS vendor adapters; called by Core only |
| `microfi-backoffice/` | Next.js 16 (React 19), Tailwind v4 | 3000 | Admin / branch manager / cashier web UI |
| `microfi-mobile/` | Flutter 3 (Dart) | — | Agent app + Client digital booklet (one codebase) |
| `kong/` | Kong 3.7, DB-less | 8000/8443 | TLS, JWT validation, rate limiting, routing |
| `prometheus/ loki/ promtail/ tempo/ grafana/` | Grafana LGTM stack | 9090 / 3100 / 3200 / 3001 | Metrics, logs, traces, dashboards |

## Commands

Full stack (Postgres, Redis, RabbitMQ, Core, Middleware, Kong — back-office and mobile run outside):

```bash
docker compose up --build
```

Core / Middleware (same commands in either directory; use `./mvnw` in Git Bash, `.\mvnw.cmd` in PowerShell):

```bash
cd microfi-core && ./mvnw spring-boot:run
```
```bash
cd microfi-core && ./mvnw test
```
```bash
cd microfi-core && ./mvnw test -Dtest=CollectionServiceTest
```
```bash
cd microfi-core && ./mvnw test -Dtest=CollectionServiceTest#recordCollectionIsIdempotent
```

Back-office (needs `MICROFI_API_BASE_URL`, default `http://localhost:8000/api/v1` — i.e. Kong, not Core directly):

```bash
cd microfi-backoffice && npm run dev
```
```bash
cd microfi-backoffice && npm run lint
```

Mobile:

```bash
cd microfi-mobile && flutter pub get && flutter run
```
```bash
cd microfi-mobile && flutter test test/core/qr_receipt_signer_test.dart
```
```bash
cd microfi-mobile && flutter analyze
```

Once the stack is up (host ports are deliberately offset to avoid clashing with other local
stacks — check `docker compose ps` rather than assuming the service's default):
Back-office `:3000`, API gateway `:8000`, Grafana `:3300` (admin/admin), Prometheus `:9091`,
Loki `:3101`, Tempo `:3200`, RabbitMQ management `:25672`, Postgres `:15432`, Redis `:16379`,
Core direct `:8080`, Middleware direct `:18081`.

`.gitlab-ci.yml` is placeholder echoes only — CI does not actually build or test anything yet.

## Request path and the JWT contract

Both clients (mobile and the back-office's Node server) talk to **Kong on :8000**, never to Core
directly, so gateway auth/rate-limiting/CORS are always exercised. Kong validates the JWT, then
Core's `JwtAuthenticationFilter` validates it again and resolves the concrete principal
(defense in depth).

The HMAC secret is duplicated in two places and **must stay byte-identical**:
`application.security.jwt.secret-key` in `microfi-core/src/main/resources/application.properties`
and the consumer `secret` in `kong/kong.yml`. Both derive the key from the raw UTF-8 bytes.
Algorithm is pinned to **HS384** and issuer to `microfi-core` (`JwtService.ISSUER`) because Kong
looks up the consumer by the `iss` claim and rejects other algorithms. Changing any of the three
breaks every request with a 401 that looks like a Core bug.

One token carries a `principalType` claim of `AGENT`, `ADMIN_USER`, or `CLIENT`; the filter routes
to the matching `UserDetailsService`. Only `/api/v1/auth/**` and the OpenAPI paths are unauthenticated.

Kong's routes are ordered by prefix specificity, and two of them exist for security reasons rather
than routing reasons: the four `forgot-password` / `reset-password` paths get their own **5/min**
budget (each hit sends a real SMS, and a 6-digit OTP is worth guessing at 20/min), and the public
route exposes `/actuator/health` only — never the bare `/actuator` prefix, which would publish
`/actuator/prometheus` to the internet. Prometheus scrapes Core and Middleware directly over the
internal Docker network, so narrowing that costs the observability stack nothing.

Back-office role gating goes through `AdminAccess.require(...)` / `requireBranchScope(...)`:
`ADMIN` is global, `BRANCH_MANAGER` and `BRANCH_CASHIER` are confined to their own branch. Any
controller acting on branch-scoped data must call both — there is no annotation-based equivalent.

## microfi-core structure

Modular monolith under `com.microfi`, one package per module
(`authentication`, `transactions`, `savings`, `registration`, `notifications`, `cbsclient`,
`events`, `shared`), each with `controller/ domain/ repository/ service/`.

Two rules the existing code follows deliberately:

- **Modules never touch another module's repository.** Cross-module reads go through the owning
  module's public `*DirectoryService` (`ClientDirectoryService`, `AgentDirectoryService`,
  `ActivationDirectoryService`). `CollectionService` is the canonical example.
- **Controllers are reactive (WebFlux), money-moving services are blocking JPA.** When a reactive
  path needs a JPA service, wrap it: `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`
  (see `JwtAuthenticationFilter` resolving a `CLIENT` principal).

Uploaded evidence (registration documents, escrow deposit proofs, variance write-off proofs,
collection-rejection proofs) goes to local disk via four sibling `*StorageService` classes that all
share the single `registration_documents` volume as subfolders — deliberately not S3/MinIO, matching
the rest of this self-hosted stack. Add new proof types the same way rather than introducing a
separate volume. Self-service password/PIN reset is OTP-over-SMS for both agents and clients, with
independently tunable expiry and attempt caps.

All DTOs live in one flat `shared/dto` package, not per module. Errors are raised as
`ResponseStatusException` with a user-facing reason string; `server.error.include-message=always`
is set precisely so those strings survive to the client.

`cbsclient/CbsClientService` is Core's **only** outbound path to the middleware, over WebClient REST
— Core→Middleware is *not* queue-based, despite the architecture document describing it that way.

`audit/` is a cross-cutting module every other module writes to. `AuditService.record` is
`@Transactional(propagation = REQUIRES_NEW)` on purpose: callers audit *rejections* immediately
before throwing and rolling back, so joining the caller's transaction would roll the audit row back
with it. It also swallows its own failures — an audit write must never fail the action it describes.

### What RabbitMQ actually carries (`config/RabbitMQConfig`, `events/`)

Four distinct flows, and the distinction between them matters:

- **Login audit events** (`microfi.auth`) — fire-and-forget, publish failures swallowed.
- **Collection + SOS reverse-geocoding** (`microfi.collection`, keys `collection.geocode` /
  `sos.geocode`) — fire-and-forget. Nominatim is rate-limited and slow; resolving a place name is
  display-only enrichment and was previously holding a Core thread for up to 5s per collection.
  Retries (`geocoding.retry.*`) run on the listener's own unbounded queue, never the bounded pool.
- **Collection recording** (`collection.record`) — **request-reply**, not fire-and-forget
  (`RabbitTemplate#convertSendAndReceive`, direct reply-to). The HTTP caller still gets a
  synchronous per-item pass/fail, because the mobile offline queue needs it and the escrow-ceiling
  check must run before any answer. The point is the *bounded* consumer pool
  (`COLLECTION_RECORD_CONSUMER_CONCURRENCY`) — a reconnection burst queues in the broker instead of
  racing for Core's threads and Postgres connections. Exceeding
  `SPRING_RABBITMQ_TEMPLATE_REPLY_TIMEOUT` (20s) surfaces as a 503.
- **SOS alerts** (`SosAlertEventRelay`) — relayed out to the Back-Office over SSE
  (`AdminSosController` → `api/sos-events/stream/route.ts` → `SosAlertListener.tsx`).

Because collection recording now depends on the broker, **RabbitMQ is required to record a
collection** — unlike on `main`, a broker outage is no longer silently tolerated on that path.

## microfi-middleware structure

`adapters/` holds one package per CBS vendor implementing `CoreBankingAdapter`; `CbsAdapterFactory`
picks one by matching `cbs.vendor` (default `mock`, backed by `MockCbsAdapter` plus an in-DB mock
ledger). Every outbound call is wrapped by `IdempotencyService` (dedupes by key, replays the stored
response) and logged to `cbs_call_log` with the correlation ID Core supplied. The middleware is
**not** routed through Kong — it is internal, reachable only from Core.

## Observability

Actuator exposes **only** `health` and `prometheus` (`management.endpoints.web.exposure.include`),
scraped over the internal Docker network. Traces go to Tempo via OTLP. Note the property namespace:
Spring Boot 4 uses `management.opentelemetry.tracing.export.otlp.endpoint` — Boot 3.x's
`management.otlp.*` no longer exists, since tracing autoconfiguration moved into its own module.
Sampling is at 1.0, which is fine at pilot volume and would need dialing down under real load.
`management.metrics.distribution.percentiles-histogram.http.server.requests=true` is what makes the
Grafana latency panels work — without it `histogram_quantile()` has no buckets to read.

## Data

Single PostgreSQL 16 instance, two schemas: `core` and `mw` (created by `init.sql`).
There is **no migration tool** — `spring.jpa.hibernate.ddl-auto=update` generates the schema, so
entity changes are the schema changes; check what an entity edit implies for existing rows.

Both services default to **H2 in-memory** in `application.properties`; Postgres is only reached
when `docker-compose.yml`'s `SPRING_DATASOURCE_*` env vars override it. Running `spring-boot:run`
locally gives you a throwaway database.

Money is `long` XAF in the smallest unit — never floating point, never `BigDecimal`. Timestamps are
`Instant` / `TIMESTAMPTZ` in UTC. Financial facts are not soft-deleted; status enums plus append-only
audit rows (`escrow_ledger`, `variance_debt`, `activation_payment`) carry the history.

## microfi-backoffice

Next.js 16 App Router. `src/proxy.ts` is the route guard — Next 16 renamed `middleware.ts` to
`proxy.ts`, and `microfi-backoffice/AGENTS.md` (auto-regenerated by `next dev`, imported by that
directory's `CLAUDE.md`) warns that this Next version differs from training data; check
`node_modules/next/dist/docs/` before writing framework code.

The session JWT lives in an httpOnly cookie and **never reaches the browser**. `src/lib/api.ts` is
server-only: Server Components call it directly; client components call a Route Handler under
`src/app/api/**` which re-proxies through the same helper and maps `ApiRequestError` back to a
status. `decodeToken` in `src/lib/auth.ts` reads claims without verifying the signature on purpose —
it is only for nav/scoping decisions, and the frontend must never hold the HMAC secret.

Dashboard sections live under `src/app/(dashboard)/`: agents (with a geofence editor), clients,
audit, collection-rejections, ofj, cashier, registrations, sos, team, tracking, settings. Report
exports are client-side via `jspdf`/`jspdf-autotable` (PDF) and `xlsx` (spreadsheet). `SosAlertListener`
holds an SSE connection for live SOS alerts.

Design tokens in `src/app/globals.css` (`@theme` block) are ported from
`Graphical Design/backoffice/high_utility_financial_core/DESIGN.md`, which is authoritative: flat
surfaces with 1px borders, no shadows/blur/gradients (they wash out on phones in direct sunlight).
UI strings live in `src/lib/i18n/dictionaries/{en,fr}.json` — the product is bilingual EN/FR.

## microfi-mobile

`lib/core/` holds cross-cutting services, `lib/features/<feature>/` holds screens plus their
repositories. `ApiClient.baseUrl` is a hardcoded `http://localhost:8000/api/v1` const — change it
there when testing against a non-local gateway.

Device binding is `DeviceIdService`: Android SSAID, Keychain UUID on iOS. It is still called
**`imei`** across the API and the DB even though it never was a hardware IMEI (unreadable on
Android 10+); keep the wire name when touching auth.

Offline collections queue into a SQLCipher-encrypted store (`offline_queue_repository.dart`) and
replay through `POST /collections/sync`. Server-side, `CollectionService` dedupes on
`(agent_id, device_tx_id)`, so a retried batch returns the original record instead of double-counting.

Localization uses ARB files in `lib/l10n/app_{en,fr}.arb` with `generate: true` — regenerate with
`flutter gen-l10n` (or `flutter pub get`) after editing; don't hand-edit `app_localizations*.dart`.

## Traceability convention

Every behavioural rule in this codebase is tagged to the specs in `Documentation/`
(`architecture.txt`, `use_cases.txt`, `priority.txt`, plus the PDFs): FR-01…FR-22 (functional),
NFR-01…NFR-12, BR-01…BR-05 (business rules), UC-01…UC-22 (use cases). Existing comments cite them
(`// BR-05: GPS mandatory`, `UC-19`, `FR-04`). Match that when adding or changing a rule — it is how
a reviewer maps code back to the requirement it implements.

The load-bearing ones to know: **BR-05/FR-12** GPS fix is a hard block before any collection;
**BR-03/FR-04** cumulative cash may not exceed the agent's escrow ceiling, evaluated server-side at
commit; **BR-02/FR-08** denomination breakdown must sum to the amount; **BR-01/FR-16** OFJ closes
only when Δ = physical − digital is resolved and nothing is still `sync_status = PENDING`.

## Testing conventions

Services get plain JUnit 5 + Mockito unit tests (`MockitoAnnotations.openMocks`, AssertJ,
`ReflectionTestUtils` for `@Value` fields). Controllers get `@WebFluxTest(controllers = X.class)`
with `@Import(SecurityConfig.class)` so the real auth chain runs, `@MockitoBean` for the service, and
`SecurityMockServerConfigurers` to inject the principal — which means every controller test must also
mock `JwtService`, `AgentDetailsService`, `AdminUserDetailsService` and `ClientDetailsService`, since
importing `SecurityConfig` pulls in `JwtAuthenticationFilter`'s dependencies.

Flutter has unit/widget tests in `test/` and device tests in `integration_test/`.

## Local dev credentials and secrets

`docker-compose.yml`, `application.properties` and `kong/kong.yml` all ship working dev values
(Postgres `microfi/microfi_password`, `ADMIN_BOOTSTRAP_LOGIN=admin` / `ChangeMe123!`, the JWT secret,
a self-signed cert in `kong/certs/`). They are deliberate for local runs and each is flagged in its
file as replace-before-deployment. Kong's admin API is bound to `127.0.0.1:8001` only — it has no
auth and leaks the JWT signing secret over `GET /consumers/{id}/jwt`, so never rebind it to `0.0.0.0`.

`docker-compose.yml` sets `SPRING_RABBITMQ_*` and `SPRING_DATA_REDIS_*` explicitly. Without them
Spring's autoconfiguration silently defaults to `localhost` inside the container, where nothing
listens — which went unnoticed on `main` precisely because every consumer of the broker there
swallowed its own failures. Now that collection recording depends on the broker, it wouldn't.

`AdminBootstrapRunner` creates the first ADMIN at startup only when no ADMIN exists; with empty
`ADMIN_BOOTSTRAP_*` it creates nothing, which is why a fresh non-Compose run has no way to log in.
SMS gateway credentials (`ORANGE_SMS_*` / `MTN_SMS_*`) are intentionally unset — sends fail gracefully
and are logged `FAILED` rather than blocking startup.
