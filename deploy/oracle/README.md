# Oracle Cloud Always Free deployment

This deployment targets an Oracle Cloud Ampere A1 VM with 2 OCPUs and 12 GB
RAM. It runs the nine existing Spring Boot applications as containers and keeps
PostgreSQL, Redis, and Kafka external. It does not create, initialize, reset, or
delete application data.

Images are published to the repository's GitHub Container Registry (GHCR)
packages. Set each package's visibility to **Private** (recommended) or
**Public** in the repository's **Packages** settings. For private packages,
create a GitHub fine-grained token with read-only package access for the
operator account and log in on the VM; never put that token in this repository
or `.env`.

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

## 2. Authenticate to GHCR

For private packages, authenticate Docker once on the VM using a GitHub account
with read-only access to the repository packages:

```bash
echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin
```

For public packages, this login is optional. Do not use a personal access token
with write access or store `GHCR_READ_TOKEN` in shell history or the repository.

## 3. Configure secrets and external services

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

## 4. Pull and start the published images

Compose performs required-variable validation before creating containers. It
pulls the selected GHCR tag; it never compiles Maven projects or builds Docker
images on the Oracle VM:

```bash
docker compose -f compose.oracle.yaml config
IMAGE_TAG=latest docker compose -f compose.oracle.yaml pull
IMAGE_TAG=latest docker compose -f compose.oracle.yaml up -d
docker compose -f compose.oracle.yaml ps
```

Every container has `restart: unless-stopped`, an init process, a 60-second
health-check grace period, and a health check against its local actuator
endpoint. Application startup failures remain visible and cause container
restarts; they are not converted into fake healthy responses.

## 5. Resource budget

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

## 6. Operations

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

## 7. Updating the application safely

Every release has an immutable `sha-<commit>` tag and a moving `latest` tag.
Prefer the immutable tag for a controlled upgrade. Pull the new images before
restarting containers so the current release remains available for rollback:

```bash
export IMAGE_TAG=sha-<commit>
docker compose -f compose.oracle.yaml config
docker compose -f compose.oracle.yaml pull
docker compose -f compose.oracle.yaml up -d --remove-orphans
docker compose -f compose.oracle.yaml ps
```

To roll back, select the previously verified commit tag and repeat the same
pull/up commands:

```bash
export IMAGE_TAG=sha-<previous-commit>
docker compose -f compose.oracle.yaml pull
docker compose -f compose.oracle.yaml up -d --remove-orphans
```

Do not use `latest` for a rollback because it is mutable. The update is rolling
only at the container replacement level; plan a maintenance window for changes
that require coordinated service behavior.
Flyway is allowed to apply the application's versioned migrations, while JPA
uses `ddl-auto=validate`. Do not run `scripts/init-databases.sql` on this VM or
against an existing database.

## 8. Backup and recovery

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

## 9. ARM64 and production boundaries

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
