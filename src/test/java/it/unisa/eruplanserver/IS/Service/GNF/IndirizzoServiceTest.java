package it.unisa.eruplanserver.IS.Service.GNF;

import it.unisa.eruplanserver.IS.Entity.GNF.ResidenzaEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.NucleoFamiliareEntity;
import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Repository.GNF.AppoggioRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.MembroRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.ResidenzaRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.RichiestaAccessoRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.NucleoFamiliareRepository;
import it.unisa.eruplanserver.IS.Repository.GUM.URRepository;
import it.unisa.eruplanserver.IS.Exception.GNF.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test Suite per Validazione Indirizzo/Residenza
 * 
 * Questa suite testa tutte le validazioni del campo "via/piazza"
 * secondo le specifiche di validazione indirizzi
 * 
 * Test Cases:
 * - TC_M_08_1: Via/Piazza troppo corta (< 1 carattere)
 * - TC_M_08_2: Via/Piazza troppo lunga (> 40 caratteri)
 * - TC_M_08_3: Via/Piazza valida
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Test Suite: Validazione Indirizzo - Via/Piazza")
class IndirizzoServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(IndirizzoServiceTest.class);

    // Messaggio del controllo finale Validator.isIndirizzoValid (CAP, provincia, regione)
    private static final String INDIRIZZO_NON_VALIDO = "Dati indirizzo non validi.";

    @Mock
    private ResidenzaRepository residenzaRepository;

        @Mock
        private URRepository urRepository;

    @InjectMocks
    private GNFServiceImpl service;

    @Mock
    private NucleoFamiliareRepository nucleoFamiliareRepository;

    // Le altre dipendenze di GNFServiceImpl: mockate anche se non usate, nessun campo resta null
    @Mock
    private RichiestaAccessoRepository richiestaRepository;

    @Mock
    private MembroRepository membroRepository;

    @Mock
    private AppoggioRepository appoggioRepository;

    @BeforeEach
    void setUp() {
        // I mock sono creati e chiusi da MockitoExtension per ogni test
        logger.info("========== INIZIO TEST SUITE VALIDAZIONE INDIRIZZO ==========");
                // Prevent NPE: stub URRepository so creaNucleoFamiliare can proceed to address validation
                when(urRepository.findByCodiceFiscale("CF123")).thenReturn(UREntity.builder().codiceFiscale("CF123").build());
    }

    /**
     * TC_M_08_1: Validazione Via Troppo Corta
     * 
     * Scenario: Utente tenta di aggiungere indirizzo con via/piazza vuota o troppo corta
     * Input: via = "" (stringa vuota)
     * Expected: ValidationException con messaggio "Nome via/piazza troppo corto"
     * Verifiche:
     *   - Eccezione lanciata
     *   - Messaggio d'errore corretto
     *   - Indirizzo NON salvato in repository
     */
    @Test
    @DisplayName("TC_M_08_1: Via/Piazza troppo corta")
    void testViaTroppoCorta_TC_M_08_1() {
        logger.info(">>> Esecuzione TC_M_08_1: Validazione via troppo corta");
        
        // ARRANGE
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("")  // ❌ Via vuota
                .civico("12")
                .comune("Napoli")
                .cap("80100")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Italia")
                .build();
        logger.debug("Residenza creata con via vuota: via='{}' (lunghezza=0)", input.getViaPiazza());

        // ACT & ASSERT
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.creaNucleoFamiliare("CF123", input, false, 0)
        );

        assertEquals("Nome via/piazza troppo corto", ex.getMessage(),
                "Il messaggio d'errore deve indicare via troppo corta");
        
        // VERIFY
        verify(residenzaRepository, never()).save(any());
        
        logger.info("<<< TC_M_08_1 COMPLETATO: Validazione via corta funziona correttamente");
    }

    /**
     * TC_M_08_2: Validazione Via Troppo Lunga
     * 
     * Scenario: Utente tenta di aggiungere indirizzo con via/piazza troppo lunga
     * Input: via = 41 caratteri (limite massimo è 40)
     * Expected: ValidationException con messaggio "Nome via/piazza troppo lungo"
     * Verifiche:
     *   - Eccezione lanciata
     *   - Messaggio d'errore corretto
     *   - Indirizzo NON salvato in repository
     */
    @Test
    @DisplayName("TC_M_08_2: Via/Piazza troppo lunga")
    void testViaTroppoLunga_TC_M_08_2() {
        logger.info(">>> Esecuzione TC_M_08_2: Validazione via troppo lunga");
        
        // ARRANGE
        String viaLunga = "A".repeat(41);  // 41 caratteri (oltre il limite)
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza(viaLunga)
                .civico("12")
                .comune("Napoli")
                .cap("80100")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Italia")
                .build();
        logger.debug("Residenza creata con via troppo lunga: lunghezza={}", input.getViaPiazza().length());

        // ACT & ASSERT
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.creaNucleoFamiliare("CF123", input, false, 0)
        );

        assertEquals("Nome via/piazza troppo lungo", ex.getMessage(),
                "Il messaggio d'errore deve indicare via troppo lunga");
        
        // VERIFY
        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_2 COMPLETATO: Validazione via lunga funziona correttamente");
    }
    // =================================================================================
    //               TEST VIA/PIAZZA CARATTERI (TC-M-08.3)
    // =================================================================================
    /**
     * TC_M_08.3:Via/Piazza con caratteri non validi
     * Scenario: Utente tenta di creare nucleo familiare con via/piazza contenente caratteri non consentiti
     * Input: via= "Via Sarti%" (contiene un carattere speciale non consentito)
     * Expected: ValidationException con messaggio specifico per caratteri non validi in via/piazza
     * Verifiche:
     * - Eccezione lanciata di tipo ValidationException
     * - Messaggio d'errore corrisponde esattamente a quello definito nel Service
     * - Indirizzo NON salvato nel repository delle residenze
     */
    @Test
    @DisplayName("TC_M_08_3: Via/Piazza con caratteri non validi")
    void testViaCaratteriNonValidi_TC_M_08_3() {
        logger.info(">>>Esecuzione TCM_M_08_3: Validazione via caratteri non validi");
        //ARRANGE
        ResidenzaEntity input=ResidenzaEntity.builder()
                .viaPiazza("Via Sarti%")
                .civico("675")
                .comune("Pompei")
                .cap("67489")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Messigno")
                .build();
        logger.debug("Residenza creata con caratteri non validi: via='{}'", input.getViaPiazza());

        //ACT & ASSERT
        ValidationException ex=assertThrows(
                ValidationException.class,
                ()-> service.creaNucleoFamiliare("CF123", input, false, null)
        );
        assertEquals("La creazione del nucleo familiare non viene effettuata dato che il campo \"Via/Piazza\" contiene caratteri non validi",
                ex.getMessage());

        //VERIFY
        verify(residenzaRepository, never()).save(any());
        logger.info("<<<TC_M_08_3 COMPLETATO");
    }
    // =================================================================================
    //               TEST CIVICO (TC-M-08.4, TC-M-08.5, TC-M-08.6)
    // =================================================================================
    /**
     * TC_M_08_4: Validazione Civico Troppo Corto
     * Scenario: Utente tenta di creare nucleo familiare con civico vuoto
     * Input: civico = "" (stringa vuota, lunghezza 0)
     * Expected: ValidationException con messaggio specifico per civico troppo corto
     * Verifiche:
     *- Eccezione lanciata prima della validazione generica dell'indirizzo
     *- Messaggio d'errore indica lunghezza insufficiente del civico
     *- Sistema non procede al salvataggio dei dati
     */
    @Test
    @DisplayName("TC_M_08_4: Civico troppo corto")
    void testCivicoTroppoCorto_TC_M_08_4() {
        logger.info(">>>Esecuzione TC_m_08_4: Validazione civico troppo corto");
        ResidenzaEntity input= ResidenzaEntity.builder()
                .viaPiazza("Via Sarti")
                .civico("")
                .comune("Pompei")
                .cap("67489")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Messigno")
                .build();
        ValidationException ex= assertThrows(ValidationException.class,() ->
                service.creaNucleoFamiliare("CF123", input, false, null)
        );
        assertEquals("La creazione del nucleo familiare non viene effettuata dato che il campo \"Civico\" è troppo corto",
        ex.getMessage());
        //VERIFY
        verify(residenzaRepository, never()).save(any());

        logger.info(">>>TC_M_08_4 COMPLETATO");
    }
    /**
     * TC_M_08_5: Validazione Civico Troppo Lungo
     * Scenario: Utente tenta di creare nucleo familiare con civico superiore a 6 caratteri
     *  Input: civico = "1234567" ( qui sono 7 caratteri,il limite massimo è 6)
     *  Expected: ValidationException con messaggio specifico per civico troppo lungo
     *  Verifiche:
     * - Eccezione lanciata durante validazione specifica del civico
     * - Messaggio d'errore indica superamento della lunghezza massima consentita
     * - Validazione avviene prima della chiamata a Validator.isIndirizzoValid
     */
    @Test
    @DisplayName("TC_M_05: Civico troppo lungo")
    void testCivicoTroppoLungo_TC_M_05() {
        logger.info(">>>Esecuzione TC_M_05: Validazione civico troppo lungo");
        ResidenzaEntity input= ResidenzaEntity.builder()
                .viaPiazza("Via Sarti")
                .civico("1234567")
                .comune("Pompei")
                .cap("67489")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Messigno")
                .build();
        ValidationException ex= assertThrows(ValidationException.class,() ->
                service.creaNucleoFamiliare("CF123", input, false, null)
        );
        assertEquals("La creazione del nucleo familiare non viene effettuata dato che il campo \"Civico\" è troppo lungo",
                ex.getMessage());

        //VERIFY
        verify(residenzaRepository, never()).save(any());
        logger.info(">>>TC_M_05 COMPLETATO");
    }
    /**TC_M_08_6: Validazione Civico con Caratteri Non Validi
     *Scenario: Utente tenta di creare nucleo familiare con civico contenente caratteri non consentiti
     * Input: civico = "67-58" (contiene il carattere '-', non permesso dalla regex)
     * Expected: ValidationException con messaggio specifico per caratteri non validi nel civico
     * Verifiche:
     *  - Eccezione lanciata durante validazione pattern del civico
     *  - Messaggio d'errore indica presenza di caratteri non validi
     *  - Regex "^[0-9a-zA-Z/\\s]+$" non accetta il trattino come carattere valido
     */
    @Test
    @DisplayName("TC_M_08_6:Civico con caratteri non validi")
    void testCivicoConCaratteriNonValidi_TC_M_08_6() {
        logger.info(">>>Esecuzione TC_M_08_6: Validazione civico con caratteri non validi");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Sarti")
                .civico("67-68")
                .comune("Pompei")
                .cap("67489")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Messigno")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, null)
        );
        assertEquals("La creazione del nucleo familiare non viene effettuata dato che il campo \"Civico\" contiene caratteri non validi",
                ex.getMessage());

        //VERIFY
        verify(residenzaRepository, never()).save(any());
        logger.info(">>>TC_M_08_6 COMPLETATO");
    }
// =================================================================================
//               TEST COMUNE (TC-M-08.7, TC-M-08.8, TC-M-08.9)
// =================================================================================

    /**TC_M_08_7: Validazione Comune Troppo Corto
     *Scenario: Utente tenta di creare nucleo familiare con comune inferiore a 2 caratteri
     * Input: comune = "P" (1 carattere, mentre il limite minimo è 2)
     * Expected: ValidationException con messaggio specifico per comune troppo corto
     * Verifiche:
     * - Eccezione lanciata durante validazione lunghezza minima del comune
     * - Messaggio d'errore indica lunghezza insufficiente
     * - Validazione avviene dopo il trim() della stringa
     */
    @Test
    @DisplayName("TC_M_08_7: Comune troppo corto")
    void testComuneTroppoCorto_TC_M_08_7() {
        logger.info(">>>Esecuzione TC_M_08_7: Validazione comune troppo corto");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Sarti")
                .civico("67")
                .comune("P")
                .cap("67489")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Messigno")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, null)
        );
        assertEquals("La creazione del nucleo familiare non viene effettuata dato che il campo \"Comune\" è troppo corto",
                ex.getMessage());

        //VERIFY
        verify(residenzaRepository, never()).save(any());
        logger.info(">>>TC_M_08_7 COMPLETATO");
    }
        /**
         * TC_M_08_8: Validazione Comune Troppo Lungo
         * Scenario: Utente tenta di creare nucleo familiare con comune superiore a 40 caratteri
         * Input: comune = 41 caratteri 'A' (limite massimo è 40)
         * Expected: ValidationException con messaggio specifico per comune troppo lungo
         * Verifiche:
         * - Eccezione lanciata durante validazione lunghezza massima del comune
         * - Messaggio d'errore indica superamento della lunghezza consentita
         * - Test verifica il boundary case (41 vs 40 caratteri)
         */
        @Test
        @DisplayName("TC_M_08_8: Comune troppo lungo")
        void testComuneTroppoLungo_TC_M_08_8(){
            logger.info(">>>EsecuzioneTC_M_08_8: Validazione comune troppo lungo");
            String lungoComune="A".repeat(41);
            ResidenzaEntity input= ResidenzaEntity.builder()
                    .viaPiazza("Via Sarti")
                    .civico("67")
                    .comune(lungoComune)
                    .cap("67489")
                    .provincia("Napoli")
                    .regione("Campania")
                    .paese("Messigno")
                    .build();
            ValidationException ex=assertThrows(ValidationException.class,()->
                    service.creaNucleoFamiliare("CF123", input, false, null)
            );
            assertEquals("La creazione del nucleo familiare non viene effettuata dato che il campo \"Comune\" è troppo lungo",
                    ex.getMessage());

            //VERIFY
            verify(residenzaRepository, never()).save(any());
            logger.info(">>>TC_M_08_8 COMPLETATO");

        }
    /**
     * TC_M_08_9: Validazione Comune con Caratteri Non Validi
     * Scenario: Utente tenta di creare nucleo familiare con comune contenente numeri
     * Input: comune = "Pomp3i" (contiene il numero '3')
     * Expected: ValidationException con messaggio specifico per caratteri non validi nel comune
     * Verifiche:
     * - Eccezione lanciata durante validazione pattern del comune
     * - Messaggio d'errore indica presenza di caratteri non validi
     *  - Regex "^\p{L}+$" accetta solo lettere (inclusi caratteri Unicode/lettere accentate)
     *   - I numeri non sono considerati caratteri validi per il comune
     */
    @Test
    @DisplayName("TC_M_08_9: Comune con caratteri non validi")
    void testComuneConCaratteriNonValidi_TC_M_08_9() {
        logger.info("Esecuzione TC_M_08_9: Validazione con caratteri non validi");
        ResidenzaEntity input= ResidenzaEntity.builder()
                .viaPiazza("Via Sarti")
                .civico("67")
                .comune("Pomp3i")
                .cap("67489")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Messigno")
                .build();
        ValidationException ex=assertThrows(ValidationException.class,()->
                service.creaNucleoFamiliare("CF123", input, false, null)
        );
        assertEquals("La creazione del nucleo familiare non viene effettuata dato che il campo \"Comune\" contiene caratteri non validi",
                ex.getMessage());

        //VERIFY
        verify(residenzaRepository, never()).save(any());
        logger.info(">>>TC_M_08_9 COMPLETATO");
    }


// =================================================================================
    //               TEST CAP (TC-M-08.10 - TC-M-08.13)
    // =================================================================================

    /**
     * TC_M_08_10: Validazione CAP Troppo Corto
     * Scenario: Utente inserisce CAP con meno di 5 cifre
     * Input: cap = "8408"
     * Expected: ValidationException
     */
    @Test
    @DisplayName("TC_M_08_10: CAP troppo corto")
    void testCapTroppoCorto_TC_M_08_10() {
        logger.info(">>> Esecuzione TC_M_08_10: Validazione CAP corto");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli")
                .cap("8408")
                .provincia("Napoli").regione("Campania").paese("Italia")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0)
        );
        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());
        logger.debug("Eccezione catturata: {}", ex.getMessage());
        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_10 COMPLETATO");
    }

    /**
     * TC_M_08_11: Validazione CAP Troppo Lungo
     * Scenario: Utente inserisce CAP con più di 5 cifre
     * Input: cap = "840844"
     * Expected: ValidationException
     */
    @Test
    @DisplayName("TC_M_08_11: CAP troppo lungo")
    void testCapTroppoLungo_TC_M_08_11() {
        logger.info(">>> Esecuzione TC_M_08_11: Validazione CAP lungo");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli")
                .cap("840844")
                .provincia("Napoli").regione("Campania").paese("Italia")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0)
        );
        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());
        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_11 COMPLETATO");
    }

    /**
     * TC_M_08_12: Validazione CAP Formato Errato (Lettere)
     * Scenario: Utente inserisce lettere nel CAP
     * Input: cap = "8408A"
     * Expected: ValidationException
     */
    @Test
    @DisplayName("TC_M_08_12: CAP con lettere")
    void testCapConLettere_TC_M_08_12() {
        logger.info(">>> Esecuzione TC_M_08_12: Validazione CAP caratteri invalidi");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli")
                .cap("8408A")
                .provincia("Napoli").regione("Campania").paese("Italia")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0)
        );
        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());

        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_12 COMPLETATO");
    }

    /**
     * TC_M_08_13: Validazione CAP Formato Errato (Simboli)
     * Scenario: Utente inserisce simboli nel CAP
     * Input: cap = "8408@"
     * Expected: ValidationException
     */
    @Test
    @DisplayName("TC_M_08_13: CAP con simboli")
    void testCapConSimboli_TC_M_08_13() {
        logger.info(">>> Esecuzione TC_M_08_13: Validazione CAP simboli");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli")
                .cap("8408@")
                .provincia("Napoli").regione("Campania").paese("Italia")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0)
        );
        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());

        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_13 COMPLETATO");
    }

    // =================================================================================
    //               TEST  PROVINCIA (TC-M-08.14 - TC-M-08.16)
    // =================================================================================

    /**
     * TC_M_08_14: Validazione Provincia Troppo Corta
     * Input: provincia = "S"
     */
    @Test
    @DisplayName("TC_M_08_14: Provincia troppo corta")
    void testProvinciaTroppoCorta_TC_M_08_14() {
        logger.info(">>> Esecuzione TC_M_08_14: Validazione Provincia corta");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli").cap("80100")
                .provincia("S")
                .regione("Campania").paese("Italia")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0)
        );
        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());

        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_14 COMPLETATO");
    }

    /**
     * TC_M_08_15: Validazione Provincia Troppo Lunga
     * Input: provincia = > 20 caratteri
     */
    @Test
    @DisplayName("TC_M_08_15: Provincia troppo lunga")
    void testProvinciaTroppoLunga_TC_M_08_15() {
        logger.info(">>> Esecuzione TC_M_08_15: Validazione Provincia lunga");
        String provinciaLunga = "N".repeat(21);
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli").cap("80100")
                .provincia(provinciaLunga)
                .regione("Campania").paese("Italia")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0)
        );
        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());

        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_15 COMPLETATO");
    }

    /**
     * TC_M_08_16: Validazione Provincia Formato Errato (Numeri)
     * Input: provincia = "Napoli1"
     */
    @Test
    @DisplayName("TC_M_08_16: Provincia con numeri")
    void testProvinciaConNumeri_TC_M_08_16() {
        logger.info(">>> Esecuzione TC_M_08_16: Validazione Provincia numeri");
        ResidenzaEntity input = ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli").cap("80100")
                .provincia("Napoli1")
                .regione("Campania").paese("Italia")
                .build();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0)
        );
        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());
        verify(residenzaRepository, never()).save(any());
        logger.info("<<< TC_M_08_16 COMPLETATO");
    }

    // =================================================================================
    //               SCENARI AGGIUNTIVI (non presenti nella Test Case Specification)
    // =================================================================================

    /** Residenza con tutti i campi validi: ogni test cambia solo il campo che vuole verificare. */
    private ResidenzaEntity.ResidenzaEntityBuilder residenzaValida() {
        return ResidenzaEntity.builder()
                .viaPiazza("Via Roma").civico("10").comune("Napoli").cap("80100")
                .provincia("Napoli").regione("Campania").paese("Italia");
    }

    private void assertRifiutata(ResidenzaEntity input, String messaggio) {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", input, false, 0));
        assertEquals(messaggio, ex.getMessage());
        verify(residenzaRepository, never()).save(any());
        verify(nucleoFamiliareRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regione troppo corta, troppo lunga o con numeri: rifiutata")
    void testRegioneNonValida() {
        assertRifiutata(residenzaValida().regione("Lazi").build(), INDIRIZZO_NON_VALIDO);
        assertRifiutata(residenzaValida().regione("C".repeat(26)).build(), INDIRIZZO_NON_VALIDO);
        assertRifiutata(residenzaValida().regione("Campania1").build(), INDIRIZZO_NON_VALIDO);
    }

    @Test
    @DisplayName("Paese mancante, troppo corto, troppo lungo o con numeri: messaggio specifico")
    void testPaeseNonValido() {
        String corto = "La creazione del nucleo familiare  non viene effettuata dato che il campo “Paese” è troppo corto.";
        assertRifiutata(residenzaValida().paese(null).build(), corto);
        assertRifiutata(residenzaValida().paese("UK").build(), corto);
        assertRifiutata(residenzaValida().paese("I".repeat(41)).build(),
                "La creazione del nucleo familiare  non viene effettuata dato che il campo “Paese” è troppo lungo.");
        assertRifiutata(residenzaValida().paese("Italia1").build(),
                "La creazione del nucleo familiare  non viene effettuata dato che il campo “Paese” contiene caratteri non validi.");
    }

    @Test
    @DisplayName("Residenza assente: rifiutata")
    void testResidenzaNull() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.creaNucleoFamiliare("CF123", null, false, 0));
        assertEquals("é obbligatorio inserire i dati della residenza.", ex.getMessage());
        verifyNoInteractions(residenzaRepository, nucleoFamiliareRepository);
    }

    @Test
    @DisplayName("Utente inesistente: nucleo non creato")
    void testUtenteInesistente() {
        when(urRepository.findByCodiceFiscale("CF123")).thenReturn(null);

        Exception ex = assertThrows(Exception.class, () ->
                service.creaNucleoFamiliare("CF123", residenzaValida().build(), false, 0));
        assertEquals("Utente non trovato con questo codice fiscale.", ex.getMessage());
        verifyNoInteractions(residenzaRepository, nucleoFamiliareRepository);
    }

    @Test
    @DisplayName("Utente già in un nucleo: nucleo non creato")
    void testUtenteGiaInNucleo() {
        UREntity giaInNucleo = UREntity.builder().codiceFiscale("CF123")
                .nucleoFamiliare(NucleoFamiliareEntity.builder().id(7L).build()).build();
        when(urRepository.findByCodiceFiscale("CF123")).thenReturn(giaInNucleo);

        Exception ex = assertThrows(Exception.class, () ->
                service.creaNucleoFamiliare("CF123", residenzaValida().build(), false, 0));
        assertEquals("l'utente fa già parte di un nucleo familiare.", ex.getMessage());
        verifyNoInteractions(residenzaRepository, nucleoFamiliareRepository);
    }

    @Test
    @DisplayName("Veicolo senza numero di posti o con più di 8 posti: rifiutato")
    void testVeicoloNonValido() {
        Exception senzaPosti = assertThrows(Exception.class, () ->
                service.creaNucleoFamiliare("CF123", residenzaValida().build(), true, null));
        assertEquals("Specifica il numero di posti che hai nel tuo veicolo.", senzaPosti.getMessage());

        Exception troppiPosti = assertThrows(Exception.class, () ->
                service.creaNucleoFamiliare("CF123", residenzaValida().build(), true, 9));
        assertEquals("Il numero di posti deve essere tra 1 e 8.", troppiPosti.getMessage());

        verifyNoInteractions(residenzaRepository, nucleoFamiliareRepository);
    }

    @Test
    @DisplayName("Dati validi con veicolo (limiti massimi dei campi): crea il nucleo e lo collega all'utente")
    void testCreazioneConVeicolo() throws Exception {
        UREntity utente = UREntity.builder().codiceFiscale("CF123").build();
        when(urRepository.findByCodiceFiscale("CF123")).thenReturn(utente);
        when(residenzaRepository.save(any(ResidenzaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nucleoFamiliareRepository.save(any(NucleoFamiliareEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ResidenzaEntity residenza = residenzaValida()
                .viaPiazza("V".repeat(40)).civico("123456").comune("N".repeat(40))
                .provincia("P".repeat(20)).regione("R".repeat(25)).paese("I".repeat(40))
                .build();

        NucleoFamiliareEntity nucleo = service.creaNucleoFamiliare("CF123", residenza, true, 8);

        assertAll(
                () -> assertSame(utente, nucleo.getAdmin()),
                () -> assertSame(residenza, nucleo.getResidenza()),
                () -> assertTrue(nucleo.isHasVeicolo()),
                () -> assertEquals(8, nucleo.getNumeroPostiVeicolo()),
                () -> assertSame(nucleo, utente.getNucleoFamiliare(), "l'utente deve entrare nel nucleo creato"));
        verify(urRepository).save(utente);
    }

    @Test
    @DisplayName("Dati validi senza veicolo: crea il nucleo senza posti veicolo")
    void testCreazioneSenzaVeicolo() throws Exception {
        UREntity utente = UREntity.builder().codiceFiscale("CF123").build();
        when(urRepository.findByCodiceFiscale("CF123")).thenReturn(utente);
        when(residenzaRepository.save(any(ResidenzaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nucleoFamiliareRepository.save(any(NucleoFamiliareEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        NucleoFamiliareEntity nucleo = service.creaNucleoFamiliare("CF123", residenzaValida().build(), false, null);

        ArgumentCaptor<NucleoFamiliareEntity> salvato = ArgumentCaptor.forClass(NucleoFamiliareEntity.class);
        verify(nucleoFamiliareRepository).save(salvato.capture());
        assertAll(
                () -> assertFalse(salvato.getValue().isHasVeicolo()),
                () -> assertEquals(0, salvato.getValue().getNumeroPostiVeicolo()),
                () -> assertSame(nucleo, utente.getNucleoFamiliare()));
    }
}
