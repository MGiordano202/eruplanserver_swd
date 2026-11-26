# Verifica formale con JML

Contratti JML su validazioni, utility ed entity, verificati con **OpenJML 21.0.29** in modalità ESC (statica) e RAC (runtime).

## Setup

- OpenJML in `C:\openjml`, nel `PATH`; Z3 incluso nella distribuzione.
- Lombok genera codice in compilazione: i sorgenti vengono delombokati in `target/delombok` e OpenJML lavora su quella copia.
- `scripts/jml/verify.sh` (`esc`, `rac` o entrambi) rende la verifica riproducibile con un solo comando: classpath Maven, delombok, ESC, RAC. I log vanno in `target/jml/`.
- ESC dimostra un metodo alla volta su un solo thread: lo script divide le classi in 7 gruppi eseguiti in parallelo, con un timeout di 30 s per query. Una verifica completa richiede circa 17 minuti.
- Entity con `--nullable-by-default` (campi e getter Lombok possono essere null); `Validator` e `Utility` col default JML non-null e parametri nullabili annotati `/*@ nullable @*/`.
- `scripts/jml/JmlRacDriver.java`: RAC controlla solo il codice eseguito, quindi il driver esercita i metodi annotati con casi validi, casi limite e chiamate fuori contratto.

## Specifiche aggiunte

| Classe | Specifiche |
| :--- | :--- |
| `NucleoFamiliareEntity` | `invariant 0 <= numeroPostiVeicolo <= 8`; setter con `requires 0..8`, `assignable`, `ensures` |
| `Punto` | `ensures` sui costruttori; `equals` pure, riflessivo, `false` con null o altro tipo |
| `ZonaSicura` | `ensures` sui costruttori; `getRaggio` pure |
| `Validator` | contratti esatti `\result <==> ...` per `isTroppoCorto`, `isTroppoLungo`, `isCodiceFiscaleLengthValid`, `isAssistenzaDefinita`, `isMinorenneDefinito`; `null/vuoto ==> false` per i metodi con regex; `normal_behavior` ed `exceptional_behavior` per `creaZoneSicure` (raggio 50–250) e `creaZonaPericolo` (≥ 4 punti); `requires` lista non vuota per `checkConnessione` |
| `Utility.encrypt` | `requires string != null`, `ensures \result.length() >= 64`, invariante e variante di ciclo |

## Risultati ESC

**Dimostrate:** costruttori ed `equals` di `Punto`, costruttori e `getRaggio` di `ZonaSicura`, i 5 helper di `Validator` con contratto esatto, `checkConnessione`, setter e costruttore vuoto di `NucleoFamiliareEntity`.

Problemi trovati e risolti:

| Segnalazione | Causa | Soluzione |
| :--- | :--- | :--- |
| `ensures this.lat == lat` falsa | con `NaN`, `==` è falso | postcondizione con il caso `Double.isNaN` |
| campi non usabili nelle spec | privati / package-private | `spec_public` |
| 14 null-dereference ripetute | costanti `Pattern` viste come nullabili | `non_null` sulle costanti statiche |
| underflow su `punti.size() - 1` | senza `assignable` la lista poteva cambiare | `assignable \nothing` |
| invarianti di ciclo in `creaZoneSicure` | iteratore del for-each non modellato | getter con contratto, ciclo con indice |

Non dimostrate:

| Specifica | Perché | Copertura |
| :--- | :--- | :--- |
| invariante di `NucleoFamiliareEntity` nel costruttore Lombok | **difetto reale**: il costruttore usato da `@Builder` non controlla i posti e ne accetta 9 | controlli 1–8 in `GNFServiceImpl`; confermato dal RAC |
| regex di `Validator`, `isDataNascitaValid`, `encrypt` | **limite delle librerie**: le specifiche OpenJML di `Pattern`, `LocalDate`, `BigInteger` ammettono eccezioni; renderle dimostrabili richiederebbe `assume` | RAC e test JUnit |
| `creaZoneSicure`, `creaZonaPericolo` | **limite del dimostratore**: i quantificatori su `List.get(i)` non si chiudono entro il timeout | RAC: bordi 50/250, poligoni aperti e chiusi |
| `toString` dei builder di `ResidenzaEntity` e `UREntity` | codice Lombok di altre classi | fuori ambito |

## Risultati RAC

Gli **8 scenari validi** passano senza violazioni: casi limite 50/250, poligono chiuso, posti 0 e 8, hash di 64 caratteri, eccezioni previste. Le **4 chiamate fuori contratto** sono tutte rilevate:

| Chiamata | Violazione |
| :--- | :--- |
| `checkConnessione(List.of())` | precondizione, poi `ArrayIndexOutOfBounds` |
| `setNumeroPostiVeicolo(-1)` | precondizione e invariante |
| `builder().numeroPostiVeicolo(9).build()` | invariante all'uscita del costruttore |
| `encrypt(null)` | precondizione, poi `NullPointerException` |

Problemi dello strumento: crash interno (`MISMATCHED BLOCKS`) su `String.getBytes()`, risolto con `getBytes(StandardCharsets.UTF_8)` (identico su Java 21); driver lanciato con `openjml-run` e compilato con `--release 21`.

I 420 test JUnit restano verdi.

## Lezioni apprese

- Una verifica fallita va interpretata: `NaN` era una specifica troppo forte, il builder un vincolo davvero aggirabile, `Pattern` un limite dello strumento.
- Clausole come `assignable` e `non_null` pesano quanto `requires`/`ensures`: senza, il dimostratore assume il caso peggiore e produce falsi allarmi.
- Con Lombok, per specificare un getter o un setter conviene scriverlo a mano.
- ESC dà garanzie per tutti gli input ma si ferma sulle librerie non modellate; RAC copre quei casi solo sulle esecuzioni provate. Insieme sono complementari.
