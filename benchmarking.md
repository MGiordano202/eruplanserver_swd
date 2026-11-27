# Microbenchmarking

Misure con **JMH 1.37** esteso da **AMBER** (`1.37-extended`), su MacBook Apple Silicon con JDK 21, a computer inattivo.

## Configurazione

- Profilo Maven `benchmarks`: `./mvnw -Pbenchmarks package` crea `target/benchmarks.jar`; build normale, CI e Docker non cambiano. Il jar di AMBER è in `libs/`, installato con `install:install-file` come `1.37-extended`.
- Benchmark in `src/jmh/java`, package `benchmarks`.

| Benchmark | Modalità | Warm-up | Misura | Fork |
| :--- | :--- | :--- | :--- | ---: |
| Scritti a mano | `AverageTime` | 5 × 1 s | 5 × 1 s | 2 |
| Convertiti con JU2JMH | `Throughput` | 3 × 1 s | 5 × 1 s | 1 |
| AMBER (`@DynamicHalt(model = "oscnn")`) | `AverageTime` | deciso dal modello | ultime 100 iterazioni | 2 |

Stato preparato in `@Setup`; risultati restituiti o passati a un `Blackhole` contro l'eliminazione del codice morto.

## Metodi scelti

- **`Utility.encrypt`**: hash SHA-256 a ogni registrazione e login. Confrontato con un'alternativa con `HexFormat` (Java 17); il `@Setup` verifica che i due hash coincidano.
- **`Validator`**: codice fiscale, password, indirizzo (7 regex), data di nascita valida e non valida, usati a ogni richiesta con dati anagrafici.
- **`Validator.creaZonaPericolo` e `ZonaPericoloConverter`**: validazione del poligono e conversione JSON a ogni salvataggio e lettura di un piano, con 4, 100 e 1000 punti.

## Risultati

| Benchmark | Tempo medio |
| :--- | ---: |
| `encrypt` attuale, `Password1` | 396 ± 19 ns |
| `encrypt` attuale, passphrase di 72 caratteri | 427 ± 26 ns |
| `encrypt` con `HexFormat`, `Password1` | 70 ± 2 ns |
| `encrypt` con `HexFormat`, passphrase | 90 ± 3 ns |
| codice fiscale | 55 ± 1 ns |
| password | 72 ± 9 ns |
| data di nascita valida | 73 ± 2 ns |
| **data di nascita non valida** | **1296 ± 50 ns** |
| indirizzo | 206 ± 5 ns |

| Zona di pericolo (µs) | 4 punti | 100 punti | 1000 punti |
| :--- | ---: | ---: | ---: |
| validazione | 0,03 | 0,82 | 9,1 |
| verso JSON | 0,32 | 8,7 | 110 |
| da JSON | 0,85 | 32 | 333 |

## Colli di bottiglia

1. **Conversione esadecimale di `encrypt`.** Le due versioni calcolano lo stesso SHA-256: la differenza (396 − 70 = 326 ns, l'82% del tempo) è la conversione con `BigInteger` e il ciclo che aggiunge gli zeri. `HexFormat` è 4,7–5,7 volte più veloce.
2. **Eccezioni nel flusso normale.** Una data inesistente (`31/02/1990`) costa 18 volte una data valida: `LocalDate.parse` lancia un'eccezione che il `Validator` cattura, e creare un'eccezione registra tutto lo stack.
3. **JSON delle zone.** Tutti i costi crescono in modo lineare (10 volte i punti, circa 10 volte il tempo). Con 1000 punti salvare e rileggere costa 443 µs, 48 volte la validazione; la lettura costa 3 volte la scrittura e avviene a ogni caricamento del piano.

## JU2JMH

Convertiti `UtilityTest#encrypt_deterministico` ed `encrypt_lunghezzaFissa` (funzioni pure, senza mock). JU2JMH richiede Java 17: eseguito nel container `eclipse-temurin:17-jdk`.

| Benchmark convertito | Throughput | Tempo per chiamata |
| :--- | ---: | ---: |
| `encrypt_deterministico` | 1.050.749 ops/s | 0,95 µs |
| `encrypt_lunghezzaFissa` | 8.768 ops/s | 114 µs |

`encrypt_deterministico` fa 2 hash e un `assertEquals`: circa 475 ns per hash, coerente con i 396 ns del benchmark scritto a mano più il costo dell'asserzione. Un test convertito misura anche le asserzioni (in `encrypt_lunghezzaFissa` `String.matches` ricompila la regex a ogni hash): utile per le regressioni, meno preciso per localizzare i costi.

Limiti: converte solo metodi `public` (stile JUnit 4), quindi `UtilityTest` e i due metodi sono stati resi pubblici; copia inoltre nell'output tutti i file di test, poi rimossi.

## AMBER

`jpt_service` costruito per `linux/arm64`: l'immagine `amd64` emulata con Rosetta si arresta all'avvio (`random_device could not be read`).

| Benchmark | Warm-up fisso | AMBER (oscnn) |
| :--- | ---: | ---: |
| `encrypt` | 396 ± 19 ns | 419 ± 0,6 ns |
| data non valida | 1296 ± 50 ns | 1319 ± 6 ns |

In tutti e 4 i fork il modello ha dichiarato le misure stabili al primo controllo possibile (100ª iterazione): i risultati coincidono entro il 6% con quelli a warm-up fisso, quindi 5 s di warm-up erano già sufficienti. L'esecuzione è però durata **66 minuti**, contro circa 40 s con la configurazione fissa: il modello serve una finestra di 100 iterazioni e, senza `@Warmup`, ogni iterazione dura i 10 s predefiniti di JMH (100 × 10 s × 4 fork ≈ 66 min). Con AMBER conviene quindi impostare iterazioni brevi.

## Miglioramenti

Nessuna modifica al codice di produzione in questo lab. Il benchmark dimostra che sostituire la conversione di `encrypt` con `HexFormat` la renderebbe circa 5 volte più veloce. Per le password, però, la velocità non è un pregio: SHA-256 senza sale è veloce anche per chi tenta di indovinarle, e il miglioramento più utile sarebbe un algoritmo lento di proposito (bcrypt, Argon2).
