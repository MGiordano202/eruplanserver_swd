package it.unisa.eruplanserver.IS.Service.GE;

import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Repository.GE.GestioneEvacuazione;
import it.unisa.eruplanserver.IS.Utility.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestioneEvacuazioniTest {

    private static final Logger logger = LoggerFactory.getLogger(GestioneEvacuazioniTest.class);

    private static final String UTENTE_NON_TROVATO = "La segnalazione degli utenti arrivati nella zona sicura non viene effettuata dato che il campo “CodiceFiscale” non ha nessuna corrispondenza con un utente sul DB.";

    @Mock
    private GestioneEvacuazione geRepository;

    @InjectMocks
    private GEService geService;

    @BeforeEach
    void setup() {
        // I mock sono creati e chiusi da MockitoExtension per ogni test
        logger.info("========== INIZIO TEST SUITE GESTIONE EVACUAZIONI (GE) ==========");
    }

    /* ===========================
       TC-M-20.1: lunghezza del Codice Fiscale
       =========================== */
    @Test
    @DisplayName("TC-M-20.1: Codice Fiscale di lunghezza diversa da 16 rifiutato")
    void invalidLength() {
        // Codice fiscale con lunghezza != 16 (11, 15 e 17 caratteri)
        assertFalse(Validator.isCodiceFiscaleLengthValid("GRT36TGHH53"), "Atteso false: 11 caratteri");
        assertFalse(Validator.isCodiceFiscaleLengthValid("GRT36T56GHH5334"), "Atteso false: 15 caratteri");
        assertFalse(Validator.isCodiceFiscaleLengthValid("GRT36T56GHH5334GX"), "Atteso false: 17 caratteri");
        // Controprova: con 16 caratteri il controllo passa
        assertTrue(Validator.isCodiceFiscaleLengthValid("GRT36T56GHH5334G"), "Atteso true: 16 caratteri");
    }

    /* ===========================
       TC-M-20.2: caratteri del Codice Fiscale
       =========================== */
    @Test
    @DisplayName("TC-M-20.2: Codice Fiscale con caratteri non ammessi rifiutato")
    void invalidCharacters() {
        // Codice fiscale lungo 16 ma contiene simboli non ammessi
        assertFalse(Validator.isCodiceFiscaleCharactersValid("GRT36T$%GHH5334G"), "Atteso false: caratteri non validi");
        // Controprova: lo stesso CF senza simboli è accettato
        assertTrue(Validator.isCodiceFiscaleCharactersValid("GRT36T56GHH5334G"), "Atteso true: solo lettere e cifre 1-9");
    }

        /* ===========================
       TC-M-20.3: caratteri del Codice Fiscale
       =========================== */

    @Test
    @DisplayName("TC-M-20.3: Utente non trovato (Codice Fiscale inesistente)")
    void TC_M_20_3_UtenteNonTrovato() {
        logger.info(">>> Esecuzione TC-M-20.3: Utente non trovato");

        String cfInput = "GRT36T56GHH5334G";

        // Simuliamo che il DB non trovi nulla (ritorna null)
        when(geRepository.findByCodiceFiscale(cfInput)).thenReturn(null);

        Exception exception = assertThrows(Exception.class, () -> geService.segnalaSalvo(cfInput));

        assertEquals(UTENTE_NON_TROVATO, exception.getMessage(), "Il messaggio d'errore deve corrispondere al requisito");

        // Verifichiamo che il service abbia effettivamente chiamato il repository, una sola volta
        verify(geRepository, times(1)).findByCodiceFiscale(cfInput);
        verifyNoMoreInteractions(geRepository);

        logger.info("<<< TC-M-20.3 COMPLETATO: L'eccezione è stata lanciata correttamente.");
    }


       /* ===========================
       TC-M-20.4: Utente trovato nel DB
       =========================== */

    @Test
    @DisplayName("TC-M-20.4: Segnalazione effettuata con successo")
    void TC_M_20_4_Successo() throws Exception {
        logger.info(">>> Esecuzione TC-M-20.4: Successo");

        String cfInput = "RSSMRA80A01H501U";
        UREntity utenteMock = new UREntity();
        utenteMock.setCodiceFiscale(cfInput);

        when(geRepository.findByCodiceFiscale(cfInput)).thenReturn(utenteMock);

        String risultato = geService.segnalaSalvo(cfInput);

        assertEquals("La segnalazione degli utenti arrivati nella zona sicura viene effettuata con successo", risultato);

        verify(geRepository, times(1)).findByCodiceFiscale(cfInput);
        verifyNoMoreInteractions(geRepository);
        logger.info("<<< TC-M-20.4 COMPLETATO.");
    }

    // -------------------------------------------------------------------------
    // Scenari aggiuntivi (non presenti nella Test Case Specification)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Codice Fiscale mancante (null): segnalazione rifiutata")
    void segnalaSalvo_cfNull() {
        Exception exception = assertThrows(Exception.class, () -> geService.segnalaSalvo(null));

        assertEquals(UTENTE_NON_TROVATO, exception.getMessage());
    }
}
