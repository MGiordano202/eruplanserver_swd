# Verifica JML con OpenJML

## 1. Setup della verifica

OpenJML 21.0.29 è installato in `C:\openjml` e nel `PATH`. Il progetto usa Lombok, che OpenJML non vede: i sorgenti vengono prima delombokati in `target/delombok`, usato come sourcepath, con il classpath di Maven.

Per ripetere la verifica abbiamo scritto `scripts/jml/verify.sh [esc|rac]`, che esegue delombok, ESC e RAC e salva i log in `target/jml/`. L'ESC gira in 7 gruppi paralleli con un timeout 30s per query e la verifica completa dura circa 8 minuti. Poiché il RAC controlla solo il codice eseguito, `scripts/jml/JmlRacDriver.java` chiama i metodi annotati con casi validi, casi limite e chiamate fuori contratto.

## 2. Specifiche aggiunte

| Classe | Tipo | Specifica |
|---|---|---|
| `NucleoFamiliareEntity` | invariante | `numeroPostiVeicolo` tra 0 e 8 |
| | pre/postcondizione | costruttore usato da `@Builder`: posti tra 0 e 8, altrimenti `IllegalArgumentException` |
| | pre/postcondizione | `setNumeroPostiVeicolo`: valore tra 0 e 8, poi salvato nel campo |
| `Punto`, `ZonaSicura` | postcondizione | costruttori e `getRaggio` conservano i valori ricevuti; `equals` riflessivo e falso con `null` |
| `Validator` | postcondizione | 5 helper (`isTroppoCorto`, `isTroppoLungo`, …): risultato esatto |
| | postcondizione | 7 validazioni con regex o data: `false` con input `null` o vuoto |
| | pre/postcondizione | `creaZoneSicure`: raggi tra 50 e 250 m, altrimenti `IllegalArgumentException` |
| | pre/postcondizione | `creaZonaPericolo`: `IllegalArgumentException` con meno di 4 punti |
| | precondizione | `checkConnessione`: lista non vuota e senza `null` |
| `Utility.encrypt` | pre/postcondizione | password non `null`; hash di almeno 64 caratteri |

## 3. Risultati ESC

### Segnalazioni

| Segnalazione | Causa | Cosa abbiamo fatto |
|---|---|---|
| campi non usabili nelle specifiche | campi privati | `spec_public` |
| costruttori di `Punto` e `ZonaSicura` | con `NaN` il confronto `==` è falso | specifica corretta per `NaN` |
| 14 possibili `null` in ogni metodo di `Validator` | costanti `Pattern` viste come nullabili | costanti `non_null` |
| underflow su `punti.size() - 1` | senza `assignable` la lista poteva cambiare | `assignable \nothing` |
| invariante di `NucleoFamiliareEntity` | **difetto reale**: `@Builder` accettava qualsiasi numero di posti | costruttore che rifiuta valori fuori range |
| regex, data di nascita, `encrypt` | librerie senza specifiche (`Matcher`, `LocalDate`) o incomplete (`MessageDigest`, `BigInteger`) | `assume` |
| `toString` generati da Lombok | codice senza specifiche, fuori ambito | esclusi (`--exclude toString`) |
| `encrypt` | specifica OpenJML di `BigInteger.toString(radix)` incoerente (segnata `FIXME`) | non risolvibile con `assume` |
| `creaZoneSicure`, `creaZonaPericolo` | il modello OpenJML di `List` non permette di dimostrare nemmeno fatti banali come `zone.size() >= 0`, né di ritrovare la precondizione sugli elementi dopo una chiamata a `size()`, è un limite delle specifiche di libreria | non risolvibile con ESC |

Le eccezioni delle librerie sono escluse con un `catch` che contiene `//@ assume false` e rilancia: a runtime non cambia nulla.

| Dove | Ipotesi |
|---|---|
| `corrisponde` (regex) | `matches()` su una regex compilata non lancia eccezioni |
| 5 validazioni con regex | se `compareTo("") != 0`, la stringa non è vuota |
| `isDataNascitaValid` | `LocalDate.now()` e `isBefore()` non lanciano eccezioni |
| `encrypt` | l'hash non è `null` e ha un byte non nullo; `toString(16)` non lancia eccezioni |

## 4. Risultati RAC

Gli 11 scenari validi (casi limite, le 7 validazioni con input nulli, vuoti e validi, il builder con 9 e -1 posti) passano senza violazioni, e le 3 chiamate fuori contratto vengono tutte rilevate: `checkConnessione(List.of())` e `encrypt(null)` violano la precondizione, `setNumeroPostiVeicolo(-1)` precondizione e invariante. Per `encrypt`, `creaZoneSicure` e `creaZonaPericolo` è l'unica verifica: raggi 50 e 250 accettati, 49, 251 e liste vuote rifiutati.

## 5. Esito

- **Verificate con ESC:** `Punto`, `ZonaSicura`, `NucleoFamiliareEntity`, `checkConnessione`, i 5 helper e le 7 validazioni di `Validator` (queste ultime con gli `assume`).
- **Verificate solo con RAC:** `encrypt` (specifica di `BigInteger` incoerente), `creaZoneSicure` e `creaZonaPericolo`.
- **Specifiche violate:** nessuna.

I 420 test JUnit passano.

## 6. Lezioni apprese

Non tutti i fallimenti sono bug: uno era una specifica sbagliata (`NaN`), uno un difetto reale (il builder), gli altri limiti dello strumento. Molti falsi allarmi sono spariti dichiarando `assignable` e `non_null`. Gli `assume` rendono esplicite le ipotesi sulle librerie senza specifiche. Con Lombok, i metodi da specificare vanno scritti a mano. ESC e RAC si completano: l'ESC vale per tutti gli input, il RAC copre i casi che l'ESC non dimostra.
