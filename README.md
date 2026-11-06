# VyapaarOS

VyapaarOS is a modular monolith for FMCG distribution workflows: retailer and salesperson ordering, AI-assisted order capture, offline retailer orders, order review, and Tally CSV export.

## Architecture

- **Backend:** Java 21, Spring Boot, Spring Security bearer JWTs, PostgreSQL, and Flyway. Backend modules are organized by domain under `Backend/Backend/src/main/java/com/vyapaaros`.
- **Frontend:** React, JavaScript, Vite, React Router, and a generated Workbox service worker under `Frontend`.
- **Persistence:** PostgreSQL is the source of truth. Flyway applies schema changes; Hibernate uses `ddl-auto=validate`.
- **Offline:** IndexedDB stores retailer pending orders, salesperson drafts, and scoped catalogue/retailer caches. Backend API responses are not cached by the service worker.

## Configuration and secrets

`Backend/Backend/src/main/resources/application.properties` is the backend configuration file, including local database, JWT, OTP, SMTP, and optional AI settings. Edit its values directly; no environment setup or Spring profile is required. Replace the sample SMTP username and app password to enable OTP email delivery. Keep real production credentials out of source control and use a secret manager for production deployments.

Generate fresh 32-byte JWT and OTP secrets in PowerShell with:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Replace the sample JWT and OTP values in `application.properties` with separate secrets. The JWT value must be Base64 and decode to at least 32 bytes; the OTP secret must contain at least 32 characters.

The Vite development server defaults to `http://localhost:8080` for API requests. `VITE_API_BASE_URL` is only needed when hosting frontend and backend separately.

## Local development

### Docker Compose

Start the local stack from the repository root:

   ```powershell
   docker compose up --build
   ```

Open `http://localhost:8081`. Docker checks backend readiness internally at `http://backend:8080/actuator/health/readiness`; the backend port is not published to the host.

The PostgreSQL data volume persists across container recreation. Do not remove it for routine restarts. Flyway runs at backend startup and validates the existing schema; Hibernate is configured to validate rather than create or delete schema objects.

### Run frontend and backend separately

Start PostgreSQL with credentials matching `application.properties`, then run the backend from `Backend/Backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

The test retailer account and its retailer membership are seeded at startup. An SMTP account is needed to receive OTPs. Run the frontend from `Frontend`:

```powershell
npm ci
npm run dev
```

For development credit testing, `app.dev-seed.credit-limit` in `Backend/Backend/src/main/resources/application.properties` sets the configured seeded retailer's credit limit. The startup seed updates only that retailer's limit; it does not alter the outstanding balance or existing orders.

The Vite dev server uses `http://localhost:8080` as its default API origin.

## Build and deployment

- Backend container: `Backend/Backend/Dockerfile` builds the Spring Boot jar without running tests, then runs it as a non-root user. The image health check uses the Spring readiness endpoint.
- Frontend container: `Frontend/Dockerfile` builds static assets and serves them through unprivileged Nginx. `Frontend/nginx.conf` supplies SPA route fallback, same-origin `/api` proxying, and basic browser security headers.
- Compose: `compose.yaml` provides PostgreSQL, backend, and frontend services with a persistent database volume and required-secret checks. For hosted PostgreSQL, provide `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` through the deployment environment and adapt/remove the local `db` service as appropriate.
- For production, terminate HTTPS at a trusted ingress/reverse proxy, set exact `CORS_ALLOWED_ORIGINS`, use managed database and secrets, configure backups and ingress rate limits, and pin container images to reviewed versions/digests.
- The SPA fallback is configured in Nginx. PWA navigation falls back to the cached app shell, and the service worker uses `NetworkOnly` for API GETs.

## Security and operational notes

- JWT requests reload the user’s active status and current database roles, so disabling an account or changing its roles revokes stale access without waiting for the token to expire.
- The API uses stateless bearer tokens, exact-origin CORS, role checks in services, and retailer assignment/membership checks for scoped operations.
- Order prices, discounts, schemes, inventory reservations, credit holds, and totals are resolved by the backend. Retailer offline working carts are not submitted until the retailer explicitly places the order. Pending orders retain their stable client order UUID across retries; cached bill figures are indicative only.
- CSV export escapes formula-leading values. AI parse output is reviewed by the user before accepted items are applied to a cart. Order text is sent to the configured AI provider for parsing.
- OTP throttling is per email. Public deployments should also enforce per-client/IP rate limits at the trusted ingress and monitor abuse.
- User, role, and account provisioning APIs are not available in this codebase. The configured retailer test account, active retailer business, and owner membership are seeded at startup; provision production accounts through an approved operational process before enabling sign-in.
- SMTP delivery, AI provider availability, TLS termination, managed secrets, database backups, and deployment ingress policies depend on external infrastructure and credentials.
- `npm audit --omit=dev` reports no production dependency vulnerabilities. The full development dependency audit reports 10 low/moderate findings in the PWA/build toolchain; npm's automatic fix proposes a breaking downgrade, so those packages were left unchanged pending a compatible upgrade path.
- Frontend logout clears the browser session; it does not revoke an already-issued JWT server-side. Access tokens expire after 15 minutes, and each request still checks the user’s current active status and roles.
- Local settings and secret values are stored in `Backend/Backend/src/main/resources/application.properties` for convenience. Do not use these local credentials for production. Rotate any credentials that were previously shared or committed; use fresh JWT and OTP secrets and real SMTP credentials before enabling sign-in by email.

## Final manual checks

After configuring deployment values, verify OTP request/verify and account revocation; role and retailer-scope denial; retailer and salesperson ordering; AI parse review before apply; price/scheme/stock authority; duplicate offline-sync retry; Order Desk approval/rejection and CSV export; Admin route restrictions; PWA install/update/offline behavior; health readiness; SPA deep links; and production CORS/API connectivity. Check 360px/390px layouts and install/offline behavior in the target browsers/devices. The automated test suites were intentionally not run for this milestone.
