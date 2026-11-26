# Strategia di testing

La suite usa **JUnit 6.1.3** e **Mockito 5.24.0** e gira con `mvn test`, in locale e in CI ([CICD.md](CICD.md)), senza database, rete o credenziali.

| Livello | Package | Test | Strumenti |
| :--- | :--- | ---: | :--- |
| Unit: service e logica di business | `IS/Service/**` | 184 | JUnit, Mockito |
| Unit: validazione e conversione | `IS/Utility/**` | 129 | JUnit parametrizzati |
| Web: HTTP → controller → service | `IS/Control/**` | 74 | `@WebMvcTest`, MockMvc |
| Persistenza: entity e query | `IS/Repository/**` | 12 | `@DataJpaTest`, H2 in memoria |

Sono mockati con Mockito i repository JPA, `FirebaseService`, `FirebaseMessaging` (`mockStatic`: nessuna notifica reale) e `HttpServletRequest`/`HttpSession`. La logica pura (`Validator`, `Utility`, i converter JSON) non è mockata, perché è proprio ciò che si verifica.

## Problemi della suite originale

La suite esistente era verde ma verificava poco. Revisionandola secondo i principi FIRST abbiamo trovato:

- **Test che passavano per il motivo sbagliato.** 21 test usavano `assertThrows(Exception.class, ...)`: qualunque eccezione li faceva passare. I TC-M-10.x fallivano tutti per "utente non trovato" invece che per il proprio scenario. TC-M-10.3 lanciava una `NumberFormatException` già nel test (`Long.valueOf("11111f1")`), prima ancora di chiamare il service.
- **Eccezioni nascoste.** 20 metodi `@SneakyThrows` trasformavano errori del setup in fallimenti poco leggibili. Sono stati rimossi.
- **Messaggi non allineati alla specifica.** Il codice e la Test Case Specification differivano per spazi e tipo di eccezione; i test lo nascondevano perché non verificavano il messaggio.
- **Livelli mancanti.** Controller (0-20% di copertura) e query non erano testati: i difetti più gravi stavano proprio lì.

## Principi FIRST

- **Fast:** gli unit test usano solo mock (circa 4 s); i contesti Spring sono caricati solo dove si testa l'integrazione.
- **Independent:** mock nuovi per ogni test (`MockitoExtension`), mock statici chiusi in `@AfterEach`, rollback dopo ogni test di persistenza.
- **Repeatable:** nessuna risorsa esterna, date relative a oggi; l'unico test sensibile all'orario viene saltato con `assumeTrue` se attraversa la mezzanotte.
- **Self-validating:** ogni test verifica il risultato esatto: tipo e messaggio dell'eccezione, entity salvata (`ArgumentCaptor`), chiamate assenti (`verify(..., never())`) e ordine delle chiamate (`InOrder`).
- **Thorough:** input validi e non validi, valori limite (nomi di 2 e 30 caratteri), campi JSON mancanti, utenti non autorizzati, errori del database e di Firebase.

## Difetti trovati

Tutti corretti, ognuno con un test di regressione:

| Area | Difetto e causa | Trovato da |
| :--- | :--- | :--- |
| GPE | Zone di pericolo non salvabili (`Punto` senza getter/setter per Jackson); zone sicure salvate senza coordinate | Unit |
| GPE | Piano con zone inviato all'API → HTTP 500: cast diretto di un `JSONObject` a `ZonaPericolo` | Web |
| GPE | Nome del piano con spazi esterni aggirava il controllo duplicati e poteva superare la colonna da 35 caratteri | Unit |
| GUM/GUW | Login senza password → `NullPointerException` invece di "password errata" | Unit |
| GNF | Date di nascita impossibili accettate (31/02): `DateTimeFormatter` senza `ResolverStyle.STRICT` | Unit |
| GNF | Nucleo senza veicolo sempre rifiutato: `null` assegnato a un campo `int` | Unit |
| GNF | Aggiornamento della residenza sempre rifiutato: la provincia veniva passata `null` al `Validator` | Web |
| GNF | `/gestoreNucleo/crea` in ricorsione JSON infinita (admin ↔ nucleo) | Web |
| GNF | Veicolo con 0 posti o con posti negativi accettato: il messaggio diceva "tra 1 e 8", ma il codice controllava solo il massimo | Coverage |
| Sicurezza | Hash delle password inclusi nelle risposte JSON | Web |
| Sicurezza | Chiunque conoscesse l'ID poteva accettare un invito destinato ad altri | Web |
| Sicurezza | `Utility.encrypt` completava gli hash a 32 caratteri invece di 64 | Coverage |

## Uso di agenti AI

I test sono stati scritti con un agente AI e poi rivisti: sono stati aggiunti i casi limite mancanti, mantenuti gli ID della Test Case Specification e dati `@DisplayName` descrittivi ai nuovi scenari. Ogni test che falliva è stato analizzato per capire se l'errore fosse nel test o nel codice: da qui vengono i difetti elencati sopra.
