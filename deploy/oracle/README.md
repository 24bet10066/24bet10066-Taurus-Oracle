# Oracle Cloud Always Free deployment

This deployment targets an Oracle Cloud Ampere A1 VM with 2 OCPUs and 12 GB
RAM. It runs the nine existing Spring Boot applications as containers and keeps
PostgreSQL, Redis, and Kafka external. It does not create, initialize, reset, or
delete application data.

## 1. Prepare the VM

Install Docker Engine and the Docker Compose v2 plugin on an Oracle Linux or
Ubuntu ARM64 VM. Confirm the host is ARM64:

```bash
uname -m
docker version
docker compose version
```

`uname -m` must report `aarch64` or `arm64`. Do not expose application ports in
the Oracle security list. The gateway is bound to `127.0.0.1` until a future
HTTPS tunnel or reverse proxy is deliberately configured.

## 2. Configure secrets and external services

From the repository root:

```bash
cp deploy/oracle/.env.example .env
chmod 600 .env
${EDITOR:-vi} .env
```

The completed `.env` must contain:

- A separate existing JDBC PostgreSQL URL, username, and password for each
  database-backed service. URLs must retain `sslmode=require`.
- The existing Upstash Redis URL, normally using `rediss://`.
- Existing Confluent Kafka bootstrap servers, API key, and API secret.
- One identical random `JWT_SECRET` for every application.
- Frontend CORS origin in `FRONTEND_URL`.
- Existing TwoFactor, Cloudinary, WhatsApp, Fast2SMS, and Razorpay credentials.
- `ADMIN_ALERT_PHONES`, `ADMIN_WHATSAPP`, and `SHOP_PHONE` as intentional
  operational configuration, not source-code defaults.

Never commit `.env` or paste secret values into issue trackers or logs.

## 3. Validate and start

Compose performs required-variable validation before creating containers:

```bash
docker compose -f compose.oracle.yaml config
docker compose -f compose.oracle.yaml build --pull
docker compose -f compose.oracle.yaml up -d
docker compose -f compose.oracle.yaml ps
```

The build is native ARM64 on the A1 VM. Dockerfiles also use BuildKit Maven
cache mounts. The Compose file keeps a per-service local build cache under
`.docker-cache/`; this directory is disposable and must not be backed up as
application data.

Every container has `restart: unless-stopped`, an init process, a 60-second
health-check grace period, and a health check against its local actuator
endpoint. Application startup failures remain visible and cause container
restarts; they are not converted into fake healthy responses.

## 4. Resource budget

The Compose limits are intentionally bounded for a 12 GB VM:

| Service | CPU limit | Memory limit |
| --- | ---: | ---: |
| API gateway | 0.35 | 768 MiB |
| Auth | 0.20 | 1 GiB |
| Job | 0.25 | 1.25 GiB |
| Parts | 0.25 | 1 GiB |
| Customer | 0.20 | 768 MiB |
| Technician | 0.25 | 1 GiB |
| Notification | 0.15 | 768 MiB |
| Payment | 0.15 | 768 MiB |
| Analytics | 0.20 | 768 MiB |
| **Total container limits** | **2.00** | **8.0 GiB** |

Each runtime uses `InitialRAMPercentage=20` and `MaxRAMPercentage=70`, with
`ExitOnOutOfMemoryError`. The remaining 4 GiB is reserved for the kernel,
Docker, filesystem cache, and short-lived build activity. Monitor actual usage
before increasing any limit; nine unrestricted JVMs are not appropriate for
this VM size.

## 5. Operations

Check status and health:

```bash
docker compose -f compose.oracle.yaml ps
docker stats
docker inspect --format '{{.Name}} {{.State.Health.Status}}' \
  $(docker compose -f compose.oracle.yaml ps -q)
```

View logs:

```bash
docker compose -f compose.oracle.yaml logs -f --tail=200 api-gateway
docker compose -f compose.oracle.yaml logs -f --tail=200
```

Restart one service or all services:

```bash
docker compose -f compose.oracle.yaml restart api-gateway
docker compose -f compose.oracle.yaml restart
```

Do not use `down -v`. There are no application database containers in this
Compose file, and deleting volumes is not a recovery procedure.

## 6. Updating the application

```bash
git fetch --prune origin
git checkout main
git pull --ff-only
docker compose -f compose.oracle.yaml config
docker compose -f compose.oracle.yaml build --pull
docker compose -f compose.oracle.yaml up -d --remove-orphans
docker image prune
```

The update is rolling only at the container replacement level; plan a
maintenance window for changes that require coordinated service behavior.
Flyway is allowed to apply the application's versioned migrations, while JPA
uses `ddl-auto=validate`. Do not run `scripts/init-databases.sql` on this VM or
against an existing database.

## 7. Backup and recovery

Back up the existing PostgreSQL, Redis, and Kafka data using the providers'
native backup/export facilities. This repository does not replace those
providers. At minimum, retain:

1. A verified PostgreSQL backup for every service database.
2. Provider-supported Redis backup/export if Redis data is operationally
   required.
3. Kafka topic retention or provider backup appropriate to the existing
   Confluent setup.
4. An encrypted copy of `.env`, stored outside the repository and VM.

Test that backups can be read before relying on them. For container recovery:

```bash
docker compose -f compose.oracle.yaml up -d
docker compose -f compose.oracle.yaml ps
docker compose -f compose.oracle.yaml logs --tail=200
```

For database recovery, restore through the external provider's documented
procedure, verify the restored schema and data, then restart the affected
service. Never use `down -v`, `CREATE DATABASE`, or a reset/init script as a
recovery step.

## 8. ARM64 and production boundaries

The nine Dockerfiles use multi-architecture Maven and Eclipse Temurin Java 21
images. Maven dependencies are JVM artifacts; no application runtime dependency
was found that requires x86 native binaries. GitHub Actions validates every
Dockerfile with Buildx and a `linux/arm64` target using QEMU, without requiring
the developer's local Docker daemon.

Production OTP verification remains enabled. The staging-only bypass is not
selected by this Compose file, and the auth service's production startup
validator rejects a disabled setting.

Only the gateway has a host port, bound to localhost. Internal services are
reachable solely over the private `serviceos` Docker network.
