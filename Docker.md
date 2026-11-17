# Containerization Report

The backend runs as two containers orchestrated by Docker Compose: the Spring Boot application (`app`) and a MySQL 8.4 database (`db`).

Files: [`Dockerfile`](Dockerfile), [`docker-compose.yml`](docker-compose.yml), [`.dockerignore`](.dockerignore).

## How to run

```bash
mkdir -p secrets
openssl rand -base64 24 > secrets/db_password.txt
openssl rand -base64 24 > secrets/db_root_password.txt
cp <firebase-service-account>.json secrets/firebase_credentials.json
docker compose up -d --build
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

## Dockerfile

- **Build stage** (`maven:3.9.9-eclipse-temurin-21`): `pom.xml` is copied and dependencies are resolved before the sources are copied. A source change therefore does not invalidate the dependency layer. A BuildKit cache mount keeps `~/.m2` between builds. The jar is then split into Spring Boot layers.
- **Runtime stage** (`eclipse-temurin:21-jre-alpine`): JRE only. The four layers are copied from the least to the most frequently changing (dependencies, loader, snapshots, application), so a code change only rebuilds a few KB.
- The process runs as the unprivileged user `spring`, starts in exec form (the JVM is PID 1 and receives `SIGTERM`), has a JVM memory limit tied to the container (`MaxRAMPercentage=75`) and terminates on `OutOfMemoryError`, so the restart policy can bring it back.
- A `HEALTHCHECK` probes `/actuator/health`, which also checks the database connection.

## docker-compose.yml

| Requirement | Implementation |
|---|---|
| Custom app container | `app`, built from the `Dockerfile` and tagged `eruplanserver:1.0.0` |
| Supporting service | `db`: `mysql:8.4.3` |
| Data durability | named volume `db-data` mounted on `/var/lib/mysql` |
| Health checks | `db`: `mysqladmin ping`; `app`: `/actuator/health` must return `UP` |
| Failure recovery | `restart: on-failure:5` on both services; `app` starts only when `db` is `service_healthy` |
| Secrets | Compose `secrets`, mounted as files in `/run/secrets` |

**Secrets management.** No password is written in the compose file, the image or the environment.
- MySQL reads its passwords from `MYSQL_PASSWORD_FILE` and `MYSQL_ROOT_PASSWORD_FILE`.
- The application loads `/run/secrets` through Spring's `configtree` (`SPRING_CONFIG_IMPORT`). Each file becomes a property named after it, so `LOCAL_DATASOURCE_PASSWORD` and `FIREBASE_CREDENTIALS` resolve with no code changes.
- The `secrets/` folder is listed in `.gitignore` and `.dockerignore`, so secrets never reach git or the build context.

## Anti-patterns avoided

1. **`latest` or floating tags**: every base image has an explicit version.
2. **Fat images with build tools**: Maven and the JDK stay in the build stage; the runtime image ships only a JRE.
3. **Running as root**: the app runs as `spring` (uid 100).
4. **Secrets in `ENV`, `ARG`, image layers or the compose file**: Compose secrets are used instead (verified: `env` inside the container contains no password or key).
5. **Copying the whole context / cache busting**: `.dockerignore` excludes `.git`, `target`, IDE files and secrets; `pom.xml` is copied before `src`.
6. **Single fat-jar layer**: Spring Boot layers are extracted.
7. **Shell-form entrypoint**: exec form is used, so signals reach the JVM and shutdown is graceful.
8. **Container with no health information**: both services have a health check, and `depends_on` waits for the database instead of a fixed `sleep`.
9. **Data inside the container filesystem**: MySQL data lives in a named volume.
10. **Exposing the database**: `db` has no `ports` and is reachable only on the internal `backend` network.
11. **Running tests in the image build**: tests run in CI ([CICD.md](CICD.md)).

## Verification

Tested with Docker 29.9 and Compose v5.7:
- Both containers reach `healthy`, and `/actuator/health` returns `UP`.
- **Durability:** a row written to MySQL is still present after `docker compose down` followed by `up`.
- **Recovery:** after the JVM is terminated (`kill -TERM 1`, exit code 143), Docker restarts it (`RestartCount=1`) and it becomes `healthy` again.
- **Least privilege:** `id` inside the container returns `uid=100(spring)`.

## Faults found while containerizing

Running the packaged jar in a clean container exposed two startup failures that the IDE had hidden:
1. In `pom.xml`, `byte-buddy` was declared with `test` scope (as a workaround for Java 23). This removed it from the runtime jar, so Hibernate could not start. The scope was removed.
2. `ZonaPericolo` was annotated with `@Entity`, although it is stored as JSON through `ZonaPericoloConverter`. Hibernate failed on its `List<Punto>` field. The JPA annotations were removed.

**Note:** the original Firebase project is no longer available. A service-account JSON with a self-generated key lets the app start locally, but push notifications fail until a real Firebase project is configured.
