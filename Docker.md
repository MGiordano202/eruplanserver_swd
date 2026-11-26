# Containerizzazione

Il backend gira in due container orchestrati da Docker Compose: l'applicazione Spring Boot (`app`) e MySQL 8.4 (`db`). File: [`Dockerfile`](Dockerfile), [`docker-compose.yml`](docker-compose.yml), [`.dockerignore`](.dockerignore). Avvio: `docker compose up -d --build`, dopo aver creato i file in `secrets/` ([secrets/README.md](secrets/README.md)).

## Difetti emersi containerizzando

Eseguire il jar in un container pulito, invece che dall'IDE, ha fatto emergere problemi che lo sviluppo locale nascondeva:

1. **L'applicazione non partiva dal jar.** Nel `pom.xml` `byte-buddy` aveva scope `test` (un workaround per Java 23), quindi mancava nel jar di produzione e Hibernate non si avviava. Nell'IDE il problema non si vedeva perché il classpath di esecuzione include anche le dipendenze di test. Lo scope è stato rimosso.
2. **Mapping JPA errato.** `ZonaPericolo` era annotata `@Entity` ma viene salvata come JSON tramite `ZonaPericoloConverter`: Hibernate falliva sul campo `List<Punto>`. Le annotazioni JPA sono state rimosse.
3. **Dipendenza esterna obbligatoria all'avvio.** Il progetto Firebase originale non esiste più e l'app non parte senza credenziali Firebase valide nel formato. Con un service account generato localmente l'app si avvia, ma le notifiche push falliscono finché non viene configurato un progetto reale. Un servizio opzionale (le notifiche) blocca l'avvio dell'intero sistema.

## Dockerfile

- **Multi-stage:** lo stage di build (`maven:3.9.9-eclipse-temurin-21`) copia prima il `pom.xml` e risolve le dipendenze, poi i sorgenti, con una cache BuildKit per `~/.m2`. Il runtime (`eclipse-temurin:21-jre-alpine`) contiene solo la JRE.
- **Layer Spring Boot** copiati dal meno al più variabile: una modifica al codice ricostruisce pochi KB.
- **Sicurezza e affidabilità:** utente non privilegiato `spring`, entrypoint in forma exec (la JVM è PID 1 e riceve `SIGTERM`), memoria legata al container (`MaxRAMPercentage=75`), uscita su `OutOfMemoryError` così la restart policy può riavviarla, `HEALTHCHECK` su `/actuator/health` (che verifica anche il database).

## docker-compose.yml

| Requisito | Implementazione |
|---|---|
| Servizio di supporto | `db`: `mysql:8.4.3`, senza `ports`, raggiungibile solo sulla rete interna `backend` |
| Durabilità | volume `db-data` su `/var/lib/mysql` |
| Health check | `mysqladmin ping`; `/actuator/health` = `UP`; `app` parte solo con `db` `service_healthy` |
| Recupero | `restart: on-failure:5` su entrambi |
| Segreti | Compose `secrets` in `/run/secrets`: MySQL li legge con `*_FILE`, Spring con `configtree`, senza modifiche al codice. `secrets/` è in `.gitignore` e `.dockerignore` |

Verificato: dopo `down`/`up` i dati restano; dopo `kill -TERM 1` Docker riavvia l'app (`RestartCount=1`) che torna `healthy`; `env` nel container non contiene password; `id` restituisce `uid=100(spring)`.

**Anti-pattern evitati:** tag `latest`, immagini con tool di build, esecuzione come root, segreti in `ENV`/`ARG`/layer/compose, contesto di build non filtrato, jar in un unico layer, entrypoint in forma shell, `sleep` al posto degli health check, dati nel filesystem del container, database esposto, test eseguiti durante la build (sono in CI, [CICD.md](CICD.md)).

## Audit del workflow di riferimento (SorrentoMarina)

| Domanda | Riferimento | Nostro `docker-build.yml` |
|---|---|---|
| Trigger | `workflow_run` su "CI/CD Pipeline" completata + `if: conclusion == 'success'` | Stesso schema. Nel riferimento però la condizione `if` rende **inutile `workflow_dispatch`**: in un avvio manuale `workflow_run` non esiste e il job viene sempre saltato. Noi accettiamo anche `event_name == 'workflow_dispatch'` |
| Segreti | `DOCKER_USERNAME` e `DOCKER_PASSWORD` (token) con `docker/login-action` | Uguale, con permessi `contents: read` |
| Multi-piattaforma | `linux/amd64`, `linux/arm64` (server ARM, Apple Silicon) | Uguale, con QEMU esplicito; la build Maven gira su `$BUILDPLATFORM`, quindi non viene emulata |
| Caching | `actions/cache` per `~/.m2` + cache `gha` di Buildx | Solo cache `gha`: Maven gira dentro il Dockerfile multi-stage, quindi lo step JDK non serve |
| Metadati | `metadata-action` con tag `latest` e `version-tag` fisso | `latest` + `sha-<commit>`: il tag fisso viene sovrascritto a ogni push, quello per commit permette di risalire al codice e fare rollback |

Altre osservazioni sul riferimento: il checkout non usa `head_sha`, quindi può pubblicare un commit più recente di quello testato ([CICD.md](CICD.md)). Inoltre il suo Dockerfile usa `openjdk:17-slim`, un'immagine deprecata e non più aggiornata, e una JDK diversa dalla 21 usata in CI. Infine copia un jar costruito fuori dal container, quindi l'immagine dipende dall'ambiente di build.
