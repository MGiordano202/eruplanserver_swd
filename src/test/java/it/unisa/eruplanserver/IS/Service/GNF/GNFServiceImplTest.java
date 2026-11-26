package it.unisa.eruplanserver.IS.Service.GNF;


import it.unisa.eruplanserver.IS.Control.GNF.GNFControl;
import it.unisa.eruplanserver.IS.Entity.GNF.AppoggioEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.NucleoFamiliareEntity;
import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Repository.GNF.AppoggioRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.MembroRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.NucleoFamiliareRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.ResidenzaRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.RichiestaAccessoRepository;
import it.unisa.eruplanserver.IS.Repository.GUM.URRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GNFServiceImplTest {

    private static final String CF_ADMIN = "RSSMRA85M01H501U";
    private static final String INDIRIZZO_NON_VALIDO = "Dati indirizzo non validi.";
    private static final String APPOGGIO_NON_TROVATO = "Appoggio non trovato.";

    @InjectMocks
    private GNFServiceImpl gnfService;

    // Tutte le dipendenze di GNFServiceImpl sono mockate, anche quelle non usate dai metodi testati:
    // nessun repository reale e nessun campo lasciato null
    @Mock
    private URRepository urRepository;

    @Mock
    private AppoggioRepository appoggioRepository;

    @Mock
    private NucleoFamiliareRepository nucleoRepository;

    @Mock
    private RichiestaAccessoRepository richiestaRepository;

    @Mock
    private MembroRepository membroRepository;

    @Mock
    private ResidenzaRepository residenzaRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @Mock
    private GNFServiceImpl gnfServiceMock;

    @InjectMocks
    private GNFControl controller;

    // ------------------------------------------------------------------
    // Dati di test condivisi (nuovi per ogni test: nessuno stato condiviso)
    // ------------------------------------------------------------------

    private final NucleoFamiliareEntity nucleo = NucleoFamiliareEntity.builder().id(1L).build();

    private UREntity adminConNucleo() {
        return UREntity.builder().codiceFiscale(CF_ADMIN).nucleoFamiliare(nucleo).build();
    }

    /** Appoggio con tutti i campi validi: ogni test cambia solo il campo che vuole verificare. */
    private AppoggioEntity.AppoggioEntityBuilder appoggioValido() {
        return AppoggioEntity.builder()
                .viaPiazza("Via Sarti")
                .civico("675")
                .cap("67489")
                .comune("Pompei")
                .provincia("Napoli")
                .regione("Campania")
                .paese("Messigno");
    }

    /** Verifica che l'aggiunta venga rifiutata per indirizzo non valido e che nulla venga salvato. */
    private void assertAppoggioRifiutato(AppoggioEntity appoggio) {
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(adminConNucleo());

        Exception ex = assertThrows(Exception.class, () -> gnfService.aggiungiAppoggio(CF_ADMIN, appoggio));

        assertEquals(INDIRIZZO_NON_VALIDO, ex.getMessage());
        verify(appoggioRepository, never()).save(any());
    }

    private void simulaSessione(String cf) {
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("codiceFiscale")).thenReturn(cf);
    }

    /* ===========================
       TC-M-09.18: Paese troppo lungo (41 caratteri)
       =========================== */
    @Test
    void testAggiuntaAppoggioTC_M_9_18() {
        assertAppoggioRifiutato(appoggioValido().paese("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee").build());
    }

    /* ===========================
       TC-M-10.5: eliminaAppoggio
       =========================== */
    @Test
    void eliminaAppoggio_success_quando_id_esiste_e_admin_appartiene_al_nucleo() throws Exception {
        // TC-M-10.5: scenario positivo
        Long idAppoggio = 2222L;
        AppoggioEntity appoggio = AppoggioEntity.builder().id(idAppoggio).nucleoFamiliare(nucleo).build();

        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(adminConNucleo());
        when(appoggioRepository.findById(idAppoggio)).thenReturn(Optional.of(appoggio));

        gnfService.rimuoviAppoggio(CF_ADMIN, idAppoggio);

        verify(appoggioRepository, times(1)).delete(appoggio);
    }

    /* ===========================
       TC-M-01.1: InviteUserInvalidLengthTest
       =========================== */
    @Test
    void whenCodiceFiscaleHasInvalidLength_thenBadRequestReturned() throws Exception {
        // TC: LC != 16
        String cfAdmin = "CFADMINEXAMPLEAA";
        String cfInvitato = "GRT36TGHH53"; // length != 16

        simulaSessione(cfAdmin);

        ResponseEntity<String> response = controller.invitaUtente(cfInvitato, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("L'invito di un utente nel proprio nucleo familiare non viene effettuato dato che il campo \"CodiceFiscale\" non è composto da 16 caratteri.",
                response.getBody());

        // service must NOT be invoked
        verifyNoInteractions(gnfServiceMock);
    }

    /* ===========================
       TC-M-01.2: InviteUserInvalidCharactersTest
       =========================== */
    @Test
    void whenCodiceFiscaleContainsInvalidCharacters_thenBadRequestReturned() throws Exception {
        // TC: LC = 16 but contains invalid characters
        String cfAdmin = "CFADMINEXAMPLEAA";
        String cfInvitato = "GRT36T$%GHH5334G"; // 16 chars but invalid chars

        simulaSessione(cfAdmin);

        ResponseEntity<String> response = controller.invitaUtente(cfInvitato, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("L'invito di un utente nel proprio nucleo familiare non viene effettuato dato che il campo \"CodiceFiscale\" contiene caratteri non validi.",
                response.getBody());

        // service must NOT be invoked
        verifyNoInteractions(gnfServiceMock);
    }

    /* ===========================
       TC-M-01.3: InviteUserNotFoundInDatabaseTest
       =========================== */
    @Test
    void whenCodiceFiscaleValidButNotInDb_thenBadRequestWithServiceMessage() throws Exception {
        // TC: LC = 16, CC valid, EDB = does not exist
        String cfAdmin = "CFADMINEXAMPLEAA";
        String cfInvitato = "GRT36T56GHH5334G"; // 16 chars valid pattern-wise

        simulaSessione(cfAdmin);

        // Simulate service throwing the "not found" exception with the TD-specific message
        String expectedServiceMessage = "L'invito di un utente nel proprio nucleo familiare non viene effettuato dato che il campo \"CodiceFiscale\" non ha nessuna corrispondenza con un utente sul DB.";
        doThrow(new Exception(expectedServiceMessage)).when(gnfServiceMock).invitaUtente(cfAdmin, cfInvitato);

        ResponseEntity<String> response = controller.invitaUtente(cfInvitato, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(expectedServiceMessage, response.getBody());

        // verify service invoked exactly once
        verify(gnfServiceMock, times(1)).invitaUtente(cfAdmin, cfInvitato);
    }

    /* ===========================
       TC-M-01.4: InviteUserSuccessTest
       =========================== */
    @Test
    void whenCodiceFiscaleValidAndExists_thenReturnOk() throws Exception {
        // TC: LC = 16, CC valid, EDB = exists
        String cfAdmin = "CFADMINEXAMPLEAA";
        String cfInvitato = "HTL34DEF7HFHJ77G"; // valid 16 chars

        simulaSessione(cfAdmin);

        // Service does not throw -> success path
        doNothing().when(gnfServiceMock).invitaUtente(cfAdmin, cfInvitato);

        ResponseEntity<String> response = controller.invitaUtente(cfInvitato, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Invito inviato con successo.", response.getBody());

        verify(gnfServiceMock, times(1)).invitaUtente(cfAdmin, cfInvitato);
    }

    /* ===========================
       TC-M-09.19: Paese con caratteri non validi
       =========================== */
    @Test
    void testAggiuntaAppoggioTC_M_9_19() {
        assertAppoggioRifiutato(appoggioValido().paese("Messigno45").build());
    }

    /* ===========================
       TC-M-09.20: Aggiunta appoggio con successo
       =========================== */
    @Test
    void testAggiuntaAppoggioTC_M_9_20() throws Exception {
        AppoggioEntity appoggio = appoggioValido().build();
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(adminConNucleo());

        gnfService.aggiungiAppoggio(CF_ADMIN, appoggio);

        ArgumentCaptor<AppoggioEntity> salvato = ArgumentCaptor.forClass(AppoggioEntity.class);
        verify(appoggioRepository, times(1)).save(salvato.capture());
        assertSame(appoggio, salvato.getValue());
        assertSame(nucleo, salvato.getValue().getNucleoFamiliare(), "l'appoggio deve essere collegato al nucleo dell'admin");
    }

    /* ===========================
       TC-M-10.1: ID appoggio nullo
       La richiesta viene fermata dal controller prima di arrivare al service.
       =========================== */
    @Test
    void testEliminazioneAppoggioTC_M_10_1() {
        simulaSessione(CF_ADMIN);

        ResponseEntity<String> response = controller.rimuoviAppoggio(null, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ID appoggio non valido.", response.getBody());
        verifyNoInteractions(gnfServiceMock);
    }

    /* ===========================
       TC-M-10.2: ID oltre il limite degli int (2147483648): nessun appoggio con quell'ID
       =========================== */
    @Test
    void testEliminazioneAppoggioTC_M_10_2() {
        Long idFuoriRange = 2147483647L + 1;
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(adminConNucleo());
        when(appoggioRepository.findById(idFuoriRange)).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () -> gnfService.rimuoviAppoggio(CF_ADMIN, idFuoriRange));

        assertEquals(APPOGGIO_NON_TROVATO, ex.getMessage());
        verify(appoggioRepository, never()).delete(any());
    }

    /* ===========================
       TC-M-10.3: ID non numerico ("11111f1")
       Un ID non numerico non può diventare un Long: Spring rifiuta la richiesta con 400
       prima di chiamare il controller, quindi il service non viene mai raggiunto.
       =========================== */
    @Test
    void testEliminazioneAppoggioTC_M_10_3() {
        assertThrows(NumberFormatException.class, () -> Long.valueOf("11111f1"));
        verifyNoInteractions(urRepository, appoggioRepository);
    }

    /* ===========================
       TC-M-10.4: ID numerico valido ma inesistente
       =========================== */
    @Test
    void testEliminazioneAppoggioTC_M_10_4() {
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(adminConNucleo());
        when(appoggioRepository.findById(1L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () -> gnfService.rimuoviAppoggio(CF_ADMIN, 1L));

        assertEquals(APPOGGIO_NON_TROVATO, ex.getMessage());
        verify(appoggioRepository, never()).delete(any());
    }

    // -------------------------------------------------------------------------
    // TEST CASE SPECIFICATION: UC-M-09 (Aggiunta Appoggio)
    // -------------------------------------------------------------------------

    /*
     * TC-M-09.4: Civico troppo corto
     * Input: Civico = "" (vuoto)
     */
    @Test
    void testAggiuntaAppoggioTC_M_09_04() {
        assertAppoggioRifiutato(appoggioValido().civico("").build());
    }

    /*
     * TC-M-09.5: Civico troppo lungo
     * Input: Civico = "67598069"
     */
    @Test
    void testAggiuntaAppoggioTC_M_09_05() {
        assertAppoggioRifiutato(appoggioValido().civico("67598069").build());
    }

    /*
     * TC-M-09.6: Civico contiene caratteri non validi
     * Input: Civico = "67-58"
     */
    @Test
    void testAggiuntaAppoggioTC_M_09_06() {
        assertAppoggioRifiutato(appoggioValido().civico("67-58").build());
    }

    /*
     * TC-M-09.7: Comune troppo corto
     * Input: Comune = "P"
     */
    @Test
    void testAggiuntaAppoggioTC_M_09_07() {
        assertAppoggioRifiutato(appoggioValido().comune("P").build());
    }

    /*
     * TC-M-09.8: Comune troppo lungo
     * Input: Comune = 41 caratteri '41 b'
     */
    @Test
    void testAggiuntaAppoggioTC_M_09_08() {
        assertAppoggioRifiutato(appoggioValido().comune("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb").build());
    }

    /*
     * TC-M-09.9: Comune contiene caratteri non validi
     * Input: Comune = "Pomp£i"
     */
    @Test
    void testAggiuntaAppoggioTC_M_09_09() {
        assertAppoggioRifiutato(appoggioValido().comune("Pomp£i").build());
    }

    /*
     * TC-M-09.10: CAP formato non valido
     * Input: CAP = "674" (non 5 cifre)
     */
    @Test
    void testAggiuntaAppoggioTC_M_09_10() {
        assertAppoggioRifiutato(appoggioValido().cap("674").build());
    }

    // -------------------------------------------------------------------------
    // Scenari aggiuntivi (non presenti nella Test Case Specification)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Aggiunta appoggio: utente inesistente rifiutato senza salvare")
    void aggiuntaAppoggio_utenteInesistente() {
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(null);

        Exception ex = assertThrows(Exception.class, () -> gnfService.aggiungiAppoggio(CF_ADMIN, appoggioValido().build()));

        assertEquals("Utente non autorizzato o nucleo non esistente.", ex.getMessage());
        verify(appoggioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aggiunta appoggio: utente senza nucleo rifiutato senza salvare")
    void aggiuntaAppoggio_utenteSenzaNucleo() {
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(UREntity.builder().codiceFiscale(CF_ADMIN).build());

        Exception ex = assertThrows(Exception.class, () -> gnfService.aggiungiAppoggio(CF_ADMIN, appoggioValido().build()));

        assertEquals("Utente non autorizzato o nucleo non esistente.", ex.getMessage());
        verify(appoggioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rimozione appoggio: utente inesistente rifiutato senza cercare né eliminare l'appoggio")
    void rimozioneAppoggio_utenteInesistente() {
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(null);

        Exception ex = assertThrows(Exception.class, () -> gnfService.rimuoviAppoggio(CF_ADMIN, 1L));

        assertEquals("Utente non autorizzato.", ex.getMessage());
        verifyNoInteractions(appoggioRepository);
    }

    @Test
    @DisplayName("Rimozione appoggio: un appoggio di un altro nucleo non può essere eliminato")
    void rimozioneAppoggio_altroNucleo() {
        NucleoFamiliareEntity altroNucleo = NucleoFamiliareEntity.builder().id(99L).build();
        AppoggioEntity appoggioAltrui = AppoggioEntity.builder().id(5L).nucleoFamiliare(altroNucleo).build();
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(adminConNucleo());
        when(appoggioRepository.findById(5L)).thenReturn(Optional.of(appoggioAltrui));

        Exception ex = assertThrows(Exception.class, () -> gnfService.rimuoviAppoggio(CF_ADMIN, 5L));

        assertEquals("Non puoi eliminare un appoggio che non ti appartiene.", ex.getMessage());
        verify(appoggioRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Aggiorna veicolo: togliere il veicolo azzera i posti senza errori")
    void aggiornaVeicolo_rimuoveVeicolo() throws Exception {
        UREntity admin = UREntity.builder().id(10L).codiceFiscale(CF_ADMIN).build();
        NucleoFamiliareEntity mioNucleo = NucleoFamiliareEntity.builder().id(1L).admin(admin)
                .hasVeicolo(true).numeroPostiVeicolo(5).build();
        admin.setNucleoFamiliare(mioNucleo);
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);

        gnfService.aggiornaVeicolo(CF_ADMIN, false, null);

        ArgumentCaptor<NucleoFamiliareEntity> salvato = ArgumentCaptor.forClass(NucleoFamiliareEntity.class);
        verify(nucleoRepository).save(salvato.capture());
        assertFalse(salvato.getValue().isHasVeicolo());
        assertEquals(0, salvato.getValue().getNumeroPostiVeicolo());
    }

    @Test
    @DisplayName("Aggiorna veicolo: con veicolo salva il numero di posti indicato")
    void aggiornaVeicolo_conVeicolo() throws Exception {
        UREntity admin = UREntity.builder().id(10L).codiceFiscale(CF_ADMIN).build();
        NucleoFamiliareEntity mioNucleo = NucleoFamiliareEntity.builder().id(1L).admin(admin).build();
        admin.setNucleoFamiliare(mioNucleo);
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);

        gnfService.aggiornaVeicolo(CF_ADMIN, true, 4);

        verify(nucleoRepository).save(mioNucleo);
        assertTrue(mioNucleo.isHasVeicolo());
        assertEquals(4, mioNucleo.getNumeroPostiVeicolo());
    }

    @ParameterizedTest(name = "{0} posti")
    @ValueSource(ints = {1, 8})
    @DisplayName("Aggiorna veicolo: 1 e 8 posti (limiti) sono accettati")
    void aggiornaVeicolo_limitiValidi(int posti) throws Exception {
        UREntity admin = UREntity.builder().id(10L).codiceFiscale(CF_ADMIN).build();
        NucleoFamiliareEntity mioNucleo = NucleoFamiliareEntity.builder().id(1L).admin(admin).build();
        admin.setNucleoFamiliare(mioNucleo);
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);

        gnfService.aggiornaVeicolo(CF_ADMIN, true, posti);

        verify(nucleoRepository).save(mioNucleo);
        assertEquals(posti, mioNucleo.getNumeroPostiVeicolo());
    }

    @ParameterizedTest(name = "{0} posti")
    @ValueSource(ints = {0, -1, 9})
    @DisplayName("Aggiorna veicolo: meno di 1 o più di 8 posti sono rifiutati")
    void aggiornaVeicolo_postiFuoriLimite(int posti) {
        UREntity admin = UREntity.builder().id(10L).codiceFiscale(CF_ADMIN).build();
        admin.setNucleoFamiliare(NucleoFamiliareEntity.builder().id(1L).admin(admin).build());
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);

        Exception ex = assertThrows(Exception.class, () -> gnfService.aggiornaVeicolo(CF_ADMIN, true, posti));

        assertEquals("Il numero di posti deve essere tra 1 e 8.", ex.getMessage());
        verifyNoInteractions(nucleoRepository);
    }

    @Test
    @DisplayName("Aggiorna veicolo: solo l'amministratore del nucleo può modificarlo")
    void aggiornaVeicolo_nonAdmin() {
        UREntity veroAdmin = UREntity.builder().id(10L).build();
        UREntity membro = UREntity.builder().id(11L).codiceFiscale(CF_ADMIN).build();
        membro.setNucleoFamiliare(NucleoFamiliareEntity.builder().id(1L).admin(veroAdmin).build());
        when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(membro);

        Exception ex = assertThrows(Exception.class, () -> gnfService.aggiornaVeicolo(CF_ADMIN, false, null));

        assertEquals("Solo l'amministratore può modificare i dati del veicolo", ex.getMessage());
        verifyNoInteractions(nucleoRepository);
    }

    @Test
    @DisplayName("Rimozione appoggio via controller: senza login risponde 401 e non chiama il service")
    void rimozioneAppoggio_senzaLogin() {
        simulaSessione(null);

        ResponseEntity<String> response = controller.rimuoviAppoggio(1L, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(gnfServiceMock);
    }
}
