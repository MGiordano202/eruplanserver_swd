# Pipeline CI/CD

Il progetto usa tre workflow GitHub Actions. Questo documento li riassume e descrive soprattutto cosa l'introduzione della pipeline ha fatto emergere nel codice e nella configurazione.

## Workflow

| Workflow | Trigger | Cosa fa |
| :--- | :--- | :--- |
| `ci.yml` | push e pull request su `main` | `./mvnw -B verify` su `ubuntu-24.04` con JDK 21: compila, esegue i test (unit, web, persistenza su H2) e applica i quality gate JaCoCo ([coverage.md](coverage.md)). Pubblica i report Surefire e JaCoCo come artifact, il check *Maven Test Results* e i dati su Codecov |
| `mutation.yml` | come `ci.yml` + avvio manuale | PITest con quality gate ([mutation.md](mutation.md)); separato perché richiede alcuni minuti |
| `docker-build.yml` | dopo un run di `CI` riuscito su `main` | build e push dell'immagine su Docker Hub ([Docker.md](Docker.md)) |

Gli step di report girano anche quando i test falliscono (`if: always()` / `success() || failure()`), cioè proprio quando servono. Un errore di Codecov non fa fallire la build (`fail_ci_if_error: false`).

## Cosa ha rivelato la pipeline

### 1. Il primo run della CI era rosso

Non appena abbiamo attivato la CI, `mvn test` è fallito su `main`. I test erano rotti da tempo, ma nessuno se n'era accorto perché l'unico workflow esistente, quello di deploy su Azure, li saltava. All'inizio usava `-skipTest`; poi, visto che i test partivano comunque e fallivano, è passato a `-Dmaven.test.skip=true`. Invece di correggerli, li si era semplicemente spenti.

I fallimenti erano difetti reali:
- messaggi di errore di `GNFServiceImpl` diversi da quelli della Test Case Specification (spazi mancanti, `Exception` generica al posto di `ValidationException` per il civico vuoto);
- una zona di pericolo con punti ripetuti veniva accettata: il `Validator` controllava solo che il poligono fosse chiuso;
- una lista di zone sicure vuota restituiva un messaggio generico ("Lista vuota").

**Lezione:** un test che non viene eseguito automaticamente smette presto di essere affidabile.

### 2. Deploy in produzione senza test

`main_eruplanserver.yml` pubblicava su Azure a ogni push su `main`, **in parallelo** alla CI e con i test saltati: un commit che falliva la CI veniva comunque messo in produzione. L'avvio manuale pubblicava addirittura il branch `dev`. Poiché l'ambiente Azure del progetto originale non è più disponibile (come il progetto Firebase, vedi [Docker.md](Docker.md)) e i suoi secret non esistono in questo repository, il workflow falliva a ogni push ed è stato **rimosso**. Il rilascio avviene ora solo tramite `docker-build.yml`, che parte solo dopo una CI verde.

### 3. Race condition nella pubblicazione dell'immagine

Un workflow attivato da `workflow_run` esegue di default il checkout dell'ultimo commit di `main`, che può essere più recente di quello testato. `docker-build.yml` fa il checkout di `workflow_run.head_sha`, quindi l'immagine pubblicata corrisponde esattamente al codice verificato. `concurrency` evita due pubblicazioni contemporanee.

### 4. Da "i test passano" a "il codice è testato"

Con `verify` la CI applica anche i quality gate di copertura. Alla prima attivazione 6 package non li superavano ([coverage.md](coverage.md)): una build verde non garantiva che il codice nuovo fosse testato.

## Riproducibilità

- **Ambiente pulito:** ogni run parte da una VM nuova, senza file residui né configurazioni locali; nessuna dipendenza da database o credenziali (H2 in memoria, servizi esterni simulati con Mockito).
- **Versioni esplicite:** runner `ubuntu-24.04`, JDK 21 Temurin, dipendenze e plugin con versione fissata nel `pom.xml`, cache Maven gestita da `setup-java`.
- **Maven fissato:** prima la CI usava il Maven preinstallato sul runner, che cambiava con l'immagine di `ubuntu-latest`, mentre il Dockerfile usava `maven:3.9.9`: CI e immagine potevano compilare con versioni diverse. Ora i workflow usano il Maven Wrapper (`./mvnw`), fissato alla 3.9.9 come nel Dockerfile, anche in locale.
- **Limite residuo:** le action sono fissate alla versione major (`@v5`), come indicato dalla guida del corso, e non a uno SHA. Lo SHA proteggerebbe da un tag spostato i secret passati alle action di terze parti (`codecov-action`, `docker/login-action`), ma richiederebbe Dependabot per ricevere gli aggiornamenti.

## Qualità del codice

- **Rilevamento precoce:** la CI gira sulle pull request, quindi un errore viene visto prima del merge, accanto al pulsante di merge.
- **Prevenzione delle regressioni:** ogni difetto corretto ha un test di regressione ([testing.md](testing.md)) eseguito a ogni modifica.
- **Gate automatici:** copertura (JaCoCo) e mutation score (PITest) sotto soglia fanno fallire il workflow.
- **Segreti:** `CODECOV_TOKEN`, `DOCKER_USERNAME` e `DOCKER_PASSWORD` sono secret del repository; i workflow hanno permessi minimi (`contents: read`, più `checks: write` solo per il report dei test).
