# Code coverage

La copertura è misurata con **JaCoCo** durante l'esecuzione della suite descritta in [testing.md](testing.md).

## Configurazione

`jacoco-maven-plugin` **0.8.12** nel `pom.xml`, con tre execution:
- `prepare-agent` collega l'agente alla JVM dei test;
- `report` (fase `test`) genera i report HTML, XML e CSV in `target/site/jacoco/`;
- `check` (fase `verify`) applica i quality gate.

| Contatore (per ogni `PACKAGE`) | Minimo |
| :--- | ---: |
| Righe | 80% |
| Branch | 75% |
| Classi | 100% |

La regola è applicata a ogni package e non al totale, così un package ben testato non può nascondere uno scoperto. `lombok.config` marca il codice generato da Lombok come `@Generated`: senza questa impostazione getter e setter mai chiamati abbasserebbero la copertura, mentre quelli chiamati la gonfierebbero. Così le percentuali misurano solo il codice scritto a mano. La CI esegue `mvn verify`, quindi i gate sono controllati a ogni push e pull request ([CICD.md](CICD.md)).

## Analisi

Risultato di `mvn clean verify` su 420 test (tutti i gate superati):

| Metrica | Coperti | Copertura |
| :--- | ---: | ---: |
| **Righe** | 723 / 741 | **97,6%** |
| **Branch** | 403 / 422 | **95,5%** |
| Metodi | 120 / 122 | 98,4% |
| Classi | 33 / 33 | 100% |

### Cosa ha rivelato la copertura

1. **I controller non erano testati.** Il primo run dava 52% di righe e 64% di branch, con i controller tra 0 e 20%. Lì c'erano i difetti più gravi (HTTP 500 sui piani, hash delle password nelle risposte, inviti accettabili da chiunque: vedi [testing.md](testing.md)). Con i test web e di persistenza la copertura totale è salita al 90%.
2. **Un bug nascosto in un ramo mai eseguito.** L'unico ramo scoperto di `Utility.encrypt` era il ciclo che completa l'hash con zeri iniziali. Scrivendo un test per coprirlo è emerso che il ciclo completava fino a 32 caratteri invece di 64: per gli hash SHA-256 che iniziano con `0` (circa 1 su 16) il ciclo non faceva nulla e l'hash usciva di 63 caratteri.3. **Il totale nascondeva package scoperti.** Con il 90% complessivo, attivare i gate per package ha fatto fallire 6 package: `Configuration` 0%, classe dell'applicazione 25%, `Entity.GNF` 18%, `Exception.GNF` 50%, `Exception.GUM` 75% e una classe mai usata. Invece di abbassare le soglie o escludere classi, abbiamo aggiunto test per il comportamento mancante: avvio di Firebase con credenziali valide e non valide, metodo `main`, login quando la risposta HTTP non può essere inviata, DTO letti da JSON, uguaglianza di `Punto`.
4. **Un limite mancante.** Analizzando i branch scoperti di `aggiornaVeicolo` è emerso che il controllo dei posti verificava solo il massimo (`> 8`), anche se il messaggio diceva "tra 1 e 8": 0 posti o posti negativi venivano salvati, anche in `creaNucleoFamiliare`. Ora c'è anche il minimo, testato con 0, -1, 1, 8 e 9.

### Classi con copertura più bassa

| Classe | Righe | Branch | Cosa manca |
| :--- | ---: | ---: | :--- |
| `ZonaPericoloConverter` | 80% | 100% | `catch` di un errore di scrittura JSON |
| `ZonaSicuraConverter` | 83% | 100% | Come sopra |
| `Utility` | 88% | 100% | Costruttore implicito, mai chiamato |
| `URControl` | 100% | 85% | Logout con sessione attiva, parsing della data |
| `GNFServiceImpl` | 97% | 90% | Rami di validazione del nucleo familiare |

### Metodi e branch non coperti

**Per scelta** (nessun comportamento da verificare): i costruttori impliciti di `Validator` e `Utility`, gli unici 2 metodi scoperti, in classi solo statiche; il `catch` di `JsonProcessingException` in scrittura nei converter, perché serializzare oggetti fatti di numeri non può fallire (quello in lettura è coperto).

**Per mancanza di test** (lacune da colmare): `creaNucleoFamiliare` (7 branch su 56: comune o CAP `null` o vuoti); `aggiornaVeicolo` (3 su 14: utente inesistente, senza nucleo, posti non indicati); errori di `abbandonaNucleo` e `rimuoviAppoggio` in `GNFControl`; lista `null` in `GSEControl`; `URControl.logout` con sessione esistente.

## Piano di miglioramento

1. **Validazione del nucleo familiare (priorità alta).** `creaNucleoFamiliare` e `aggiornaVeicolo` hanno la maggior parte dei branch scoperti e sono un percorso critico: servono test parametrizzati per ogni campo mancante o non valido.
2. **Percorsi di errore dei controller.** Test MockMvc per i rifiuti del service in `abbandonaNucleo` e `rimuoviAppoggio` e per il logout con sessione attiva.
3. **Codice irraggiungibile.** Rendere privati i costruttori di `Validator` e `Utility`; accettare come scoperto il `catch` dei converter invece di scrivere test artificiali.
4. **Alzare le soglie.** Completati i punti precedenti, portare la soglia dei branch verso il livello attuale (95%).
5. **Oltre la copertura.** Il 97% delle righe è eseguito, ma il mutation testing ha mostrato 32 mutanti sopravvissuti proprio in codice coperto ([mutation.md](mutation.md)): eseguire una riga non significa verificarne il risultato.
