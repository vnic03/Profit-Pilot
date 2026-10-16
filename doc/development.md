# Development guide

This guide covers local setup, testing, and updates to the existing Raspberry Pi deployment.

For an overview of the project, see the [main README](../README.md). Frontend source layout and UI development notes are in the [frontend README](../web/README.md).

Unless stated otherwise, run commands from the repository root.

## Requirements

- Java 21.
- Node.js matching `^22.12.0 || >=24.0.0`, with npm.
- PostgreSQL and the `psql` client.
- Git. The Gradle wrapper is included.
- Alpha Vantage and Marketaux API keys if you enable their respective data sources.

## Get the source

```bash
git clone https://github.com/vnic03/Profit-Pilot.git
cd Profit-Pilot
```

## Set up PostgreSQL

The application currently uses manual SQL setup.

Create a database and a login role using your PostgreSQL administrator account. For example, in an administrator `psql` session:

```sql
CREATE ROLE profitpilot LOGIN;
\password profitpilot
CREATE DATABASE profitpilot OWNER profitpilot;
```

Connect as the application role:

```bash
psql -h localhost -U profitpilot -d profitpilot -W
```

For a new, empty database, create the base tables:

```sql
CREATE TABLE aktienkurse (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    open NUMERIC,
    high NUMERIC,
    low NUMERIC,
    close NUMERIC,
    volume BIGINT,
    UNIQUE (symbol, date)
);

CREATE TABLE stock_sync_status (
    symbol VARCHAR(10) PRIMARY KEY,
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ
);
```

Exit with `\q`, then apply the remaining SQL files in this order:

```bash
psql -h localhost -U profitpilot -d profitpilot -W \
  -v ON_ERROR_STOP=1 -f src/main/resources/db/api-budget.sql

psql -h localhost -U profitpilot -d profitpilot -W \
  -v ON_ERROR_STOP=1 -f src/main/resources/db/price-import-retry.sql

psql -h localhost -U profitpilot -d profitpilot -W \
  -v ON_ERROR_STOP=1 -f src/main/resources/db/import-run-status.sql
```

Use your own role and database names if they differ from these examples. Indicators are calculated from stored prices; a separate indicator table is not required.

## Configure the backend

Create `src/main/resources/application.properties`:

```properties
spring.application.name=profit-pilot
spring.profiles.active=dev
server.address=127.0.0.1
server.port=8080

spring.datasource.url=jdbc:postgresql://localhost:5432/profitpilot
spring.datasource.username=profitpilot
spring.datasource.password=${PROFITPILOT_DB_PASSWORD}

profitpilot.external-api.enabled=false
profitpilot.alpha-vantage.enabled=false
profitpilot.marketaux.enabled=false
profitpilot.sync.on-startup=false
profitpilot.sync.cron=-
```

Set `PROFITPILOT_DB_PASSWORD` in the environment used to start the backend. In Bash, you can enter it without adding the password to your shell history:

```bash
read -r -s -p "Database password: " PROFITPILOT_DB_PASSWORD
printf '\n'
export PROFITPILOT_DB_PASSWORD
```

Provider keys can be supplied through environment variables or a `.env` file in the repository root:

```dotenv
ALPHA_VANTAGE_API_KEY=replace_with_your_key
MARKETAUX_API_KEY=replace_with_your_key
```

The application reads provider keys from `.env`; set the database password separately in the backend's environment.

Both `.env` and the local `application.properties` are ignored by Git. Keep credentials out of frontend configuration.

## Run locally

Start the backend:

```bash
./gradlew bootRun
```

In a second terminal, start the frontend:

```bash
cd web
npm ci
npm run dev
```

Open [localhost:3000](http://localhost:3000). Vite forwards `/api` requests to the backend at `http://localhost:8080`.

If the backend uses a different port:

```bash
DEV_API_TARGET=http://localhost:18080 npm run dev
```

Leave `VITE_API_BASE_URL` unset for the default proxy setup.

A new database has no prices to display. Enable an import to populate it, or use an existing development database.

## Import prices

For a single-stock startup check, change these settings in your local `application.properties`:

```properties
profitpilot.external-api.enabled=true
profitpilot.alpha-vantage.enabled=true
profitpilot.sync.on-startup=true
profitpilot.sync.symbols=IBM
profitpilot.sync.request-delay-ms=15000
```

Provide your Alpha Vantage key and restart the backend. Inspect the dashboard's data-status section to see the result.

Imports respect stored price dates, request budgets, and cooldowns. A restart does not force another provider request when the stock is already current.

To enable scheduled imports for all configured stocks, use the `dev,auto` profiles:

```properties
spring.profiles.active=dev,auto
```

Remove the local `profitpilot.*` settings shown above, including `profitpilot.sync.cron=-` and `profitpilot.sync.symbols=IBM`, so the [auto profile](../src/main/resources/application-auto.properties) supplies the import configuration.

That profile checks prices on startup and at 06:10, 08:10, 10:10, 12:10, 14:10, 16:10, and 18:10 New York time. News remains disabled.

To enable company news, provide your Marketaux key and set:

```properties
profitpilot.external-api.enabled=true
profitpilot.marketaux.enabled=true
```

## Run tests

Frontend:

```bash
cd web
npm ci
npm test
npm run build
cd ..
```

The build includes TypeScript checking and writes its output to `web/build`.

Backend:

```bash
./gradlew test
```

PostgreSQL integration tests are opt-in. Use a dedicated test database and a role allowed to create and drop schemas:

```bash
export PROFITPILOT_DB_TEST=true
export PROFITPILOT_TEST_DB_URL=jdbc:postgresql://localhost:5432/profitpilot_test
export PROFITPILOT_TEST_DB_USER=profitpilot_test

read -r -s -p "Test database password: " PROFITPILOT_TEST_DB_PASSWORD
printf '\n'
export PROFITPILOT_TEST_DB_PASSWORD

./gradlew test \
  --tests '*DatabaseServiceIntegrationTest' \
  --rerun-tasks

unset PROFITPILOT_DB_TEST
```

The integration tests create and remove their own test schemas. Do not point them at the production database.

## Build the application

Build the frontend before packaging the backend:

```bash
(
  cd web
  npm ci
  npm run build
)

./gradlew test bootJar --console=plain
```

The resulting application is `build/libs/profit-pilot.jar`, containing the compiled frontend.

Local `application.properties` is excluded from the JAR. A packaged deployment needs external configuration.

## Update the Raspberry Pi

The deployment scripts update an already configured Pi. They expect an existing `profitpilot.service` and application JAR.

Build the update package:

```bash
bash scripts/build-pi-package.sh
```

This runs frontend tests and the production build, followed by backend tests and JAR packaging. PostgreSQL integration tests remain a separate check.

The package is written to `build/pi-package/`. It includes the JAR, checksum, build revision, update scripts, and import-status SQL file. Uncommitted changes are marked with a `dirty` suffix in the revision.

Copy the package contents to the Pi. Apply any required database changes before installing the new JAR.

For the existing deployment, run on the Pi from the copied package directory:

```bash
sudo bash install-pi-package.sh \
  /opt/profitpilot/profit-pilot.jar \
  http://127.0.0.1:18080

bash check-pi.sh http://127.0.0.1:18080
```

The installer verifies the checksum and service target, backs up the running JAR, restarts the service, and checks operational endpoints. Its rollback restores application code, not database changes.

The hosted application uses the `server` profile and external configuration at `/etc/profitpilot/application.properties`. Cloudflare Access provides authentication; keep the backend bound to loopback behind the tunnel.

Useful checks on the Pi:

```bash
systemctl is-active profitpilot.service cloudflared.service

sudo journalctl -u profitpilot.service \
  --since '1 hour ago' --no-pager
```

## Check application status

For local development, use these endpoints on `http://localhost:8080`:

| Endpoint | Purpose |
| --- | --- |
| `/api/operations/build` | Build revision and application timestamps |
| `/api/operations/imports` | Running import and latest completed report |
| `/api/finance/data-status` | Stored price counts, dates, and freshness |
| `/api/finance/import-budget` | Price-provider request budget |
| `/api/news/budget` | News-provider request budget |

For example:

```bash
curl -fsS http://localhost:8080/api/finance/data-status
```

Through the hosted domain, these endpoints use the same Cloudflare access policy as the website.
