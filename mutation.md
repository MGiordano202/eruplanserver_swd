# Mutation testing

Il mutation testing è fatto con **PITest**: inserisce piccoli difetti nel codice e controlla se la suite di [testing.md](testing.md) li rileva. La copertura è descritta in [coverage.md](coverage.md).

## Configurazione

`pitest-maven` **1.30.2** con `pitest-junit5-plugin` **1.2.3**, necessario per eseguire i test JUnit 6.

| Impostazione | Valore |
| :--- | :--- |
| Classi mutate | `IS.Service.*`, `IS.Utility.*`, `IS.Control.*`, `IS.Entity.GPE.Punto` |
| Test | `it.unisa.eruplanserver.*` |
| Escluse | `Configuration.*`, `EruplanServerApplication`, `IS.Exception.*`, `IS.Repository.*` |
| Mutatori | `CONDITIONALS_BOUNDARY`, `INVERT_NEGS`, `NEGATE_CONDITIONALS`, `REMOVE_CONDITIONALS` e i mutatori sui valori di ritorno (`EMPTY_`, `FALSE_`, `TRUE_`, `NULL_`, `PRIMITIVE_RETURNS`) |
| Quality gate | `mutationThreshold` 80, `coverageThreshold` 80 |
| Output | HTML, XML |

Le classi escluse non contengono logica da mutare: interfacce dei repository, eccezioni e avvio di Spring e Firebase. Tra le entity resta solo `Punto`, l'unica con codice scritto a mano (`equals`, `hashCode`). I mutatori scelti colpiscono condizioni e valori di ritorno, dove si concentra la logica di validazione. PITest gira nel workflow separato `mutation.yml` ([CICD.md](CICD.md)).

## Analisi

**Mutation score 95%**: 710 mutanti, **676 uccisi** (2 per timeout), **32 sopravvissuti**, **2 senza copertura**. Test strength 95%, copertura di riga 97%: entrambi i gate sono superati.

| Classe | Mutanti | Uccisi | Sopravvissuti | Score |
| :--- | ---: | ---: | ---: | ---: |
| `GNFServiceImpl` | 220 | 204 | 16 | 93% |
| `Validator` | 187 | 178 | 9 | 95% |
| `GNFControl` | 90 | 87 | 1 (+2 senza cop.) | 96% |
| `GSEControl` | 55 | 54 | 1 | 98% |
| `URControl` | 27 | 25 | 2 | 92% |
| `GPEControl` / `OPCControl` | 10 | 8 | 2 | 80% |
| `Punto` | 15 | 14 | 1 | 93% |
| Altri service, converter, `Utility` | 106 | 106 | 0 | 100% |

`NEGATE_CONDITIONALS` e i mutatori sui ritorni vengono quasi sempre uccisi, perché i test verificano risultati esatti. Sopravvivono soprattutto `REMOVE_CONDITIONALS` (28) e `CONDITIONALS_BOUNDARY` (3 su 17).

### Cosa ha trovato la mutation che la copertura non vedeva

1. **Codice ridondante (mutanti equivalenti).** I metodi `Validator.is...Valid` controllano `compareTo("") == 0` prima di una regex che rifiuta già la stringa vuota. `creaNucleoFamiliare` controlla `civico.trim().isEmpty()` e subito dopo `civico.length() < 1`. Rimuovere uno dei due controlli non cambia nulla, quindi nessun test può uccidere quei mutanti. Non è un limite dei test ma un problema del codice: controlli duplicati che rendono la validazione più difficile da leggere.
2. **Valori limite non verificati.** Le righe sono eseguite, ma non al confine. In `aggiornaVeicolo` il limite dei posti era testato solo con 4 posti: scrivendo i test con 0, 1, 8 e 9 è emerso che il limite minimo non esisteva, e 0 posti venivano accettati (ora corretto, vedi [testing.md](testing.md)). Mancano ancora `creaZonaPericolo` (`size() <= 3`) con esattamente 3 e 4 punti e `comune.length() < 2` con 1 e 2 caratteri. Un errore `>` / `>=` passerebbe inosservato.
3. **Guard clause mai attivate.** Controlli su input `null` o mancanti che nessun test invia: `accettaInvito` senza utente invitato, `aggiornaVeicolo` e `rimuoviAppoggio` senza nucleo, comune o CAP vuoti, codice fiscale invitato `null` in `invitaUtente`.
4. **Rami secondari dei controller.** Logout con sessione attiva (`URControl`, `OPCControl`), lista `null` in `GSEControl`, registrazione senza data di nascita, percorsi di errore di `abbandonaNucleo` e `rimuoviAppoggio` (senza copertura).

**Aree più deboli:** `GNFServiceImpl` (creazione del nucleo e aggiornamento del veicolo) e la gestione della sessione nei controller. Sono le stesse aree con meno branch coperti in [coverage.md](coverage.md).

## Piano di miglioramento

1. **Service del nucleo familiare (priorità alta).** Test parametrizzati per `creaNucleoFamiliare` e `aggiornaVeicolo` con campi `null` o vuoti e valori limite (comune di 1 e 2 caratteri), ognuno con il messaggio esatto atteso. I limiti dei posti (0, 1, 8, 9) sono già coperti.
2. **Guard clause e confini.** Testare `accettaInvito` senza utente invitato, `rimuoviAppoggio` senza nucleo e le zone di pericolo con 3 e 4 punti.
3. **Controller.** Test MockMvc per logout con sessione attiva, risultato `null` in `GSEControl`, registrazione senza data e percorsi di errore di `abbandonaNucleo` e `rimuoviAppoggio`.
4. **Mutanti equivalenti.** Eliminare i controlli ridondanti (`compareTo("")`, `civico.length() < 1`) invece di scrivere test che non possono ucciderli; verificare che `Punto.hashCode` sia diverso per punti diversi.
5. **Alzare la soglia.** Completati i punti precedenti, portare `mutationThreshold` verso il 90%.
