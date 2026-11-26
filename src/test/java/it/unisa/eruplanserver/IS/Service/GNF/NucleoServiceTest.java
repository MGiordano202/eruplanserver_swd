package it.unisa.eruplanserver.IS.Service.GNF;

import it.unisa.eruplanserver.IS.Entity.GNF.AppoggioEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.MembroEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.NucleoFamiliareEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.ResidenzaEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.RichiestaAccessoEntity;
import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Repository.GNF.AppoggioRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.MembroRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.NucleoFamiliareRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.ResidenzaRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.RichiestaAccessoRepository;
import it.unisa.eruplanserver.IS.Repository.GUM.URRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test per i metodi di {@link GNFServiceImpl} che gestiscono la vita del nucleo familiare:
 * inviti, accettazione, abbandono, membri, appoggi e residenza.
 * Tutte le dipendenze (6 repository) sono mockate.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GNFServiceImpl - inviti, membri, appoggi e residenza del nucleo")
class NucleoServiceTest {

    private static final String CF_ADMIN = "RSSMRA85M01H501U";
    private static final String CF_INVITATO = "VRDLGU75B02F839K";

    @Mock private URRepository urRepository;
    @Mock private NucleoFamiliareRepository nucleoRepository;
    @Mock private RichiestaAccessoRepository richiestaRepository;
    @Mock private MembroRepository membroRepository;
    @Mock private AppoggioRepository appoggioRepository;
    @Mock private ResidenzaRepository residenzaRepository;

    @InjectMocks
    private GNFServiceImpl service;

    private UREntity admin;
    private NucleoFamiliareEntity nucleo;

    @BeforeEach
    void setUp() {
        admin = UREntity.builder().id(1L).codiceFiscale(CF_ADMIN).build();
        nucleo = NucleoFamiliareEntity.builder().id(10L).admin(admin).build();
        admin.setNucleoFamiliare(nucleo);
    }

    @Nested
    @DisplayName("invitaUtente (RF-GNF.01)")
    class Invita {

        @Test
        @DisplayName("Admin inesistente: nessuna richiesta creata")
        void adminInesistente() {
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(null);

            Exception e = assertThrows(Exception.class, () -> service.invitaUtente(CF_ADMIN, CF_INVITATO));
            assertEquals("Admin non trovato.", e.getMessage());
            verifyNoInteractions(richiestaRepository);
        }

        @Test
        @DisplayName("Invitato inesistente: nessuna richiesta creata")
        void invitatoInesistente() {
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);
            when(urRepository.findByCodiceFiscale(CF_INVITATO)).thenReturn(null);

            Exception e = assertThrows(Exception.class, () -> service.invitaUtente(CF_ADMIN, CF_INVITATO));
            assertEquals("Utente da invitare non trovato.", e.getMessage());
            verifyNoInteractions(richiestaRepository);
        }

        @Test
        @DisplayName("Invitato già in un altro nucleo: nessuna richiesta creata")
        void invitatoGiaInNucleo() {
            UREntity invitato = UREntity.builder().id(2L).codiceFiscale(CF_INVITATO)
                    .nucleoFamiliare(NucleoFamiliareEntity.builder().id(99L).build()).build();
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);
            when(urRepository.findByCodiceFiscale(CF_INVITATO)).thenReturn(invitato);

            Exception e = assertThrows(Exception.class, () -> service.invitaUtente(CF_ADMIN, CF_INVITATO));
            assertEquals("L'utente invitato fa già parte di un altro nucleo.", e.getMessage());
            verifyNoInteractions(richiestaRepository);
        }

        @Test
        @DisplayName("Dati validi: crea una richiesta PENDING verso il nucleo dell'admin")
        void successo() throws Exception {
            UREntity invitato = UREntity.builder().id(2L).codiceFiscale(CF_INVITATO).build();
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);
            when(urRepository.findByCodiceFiscale(CF_INVITATO)).thenReturn(invitato);

            service.invitaUtente(CF_ADMIN, CF_INVITATO);

            ArgumentCaptor<RichiestaAccessoEntity> captor = ArgumentCaptor.forClass(RichiestaAccessoEntity.class);
            verify(richiestaRepository).save(captor.capture());
            assertAll(
                    () -> assertEquals("PENDING", captor.getValue().getStato()),
                    () -> assertSame(nucleo, captor.getValue().getNucleo()),
                    () -> assertSame(invitato, captor.getValue().getUtenteInvitato()));
            assertNull(invitato.getNucleoFamiliare(), "l'invitato entra nel nucleo solo dopo aver accettato");
        }
    }

    @Nested
    @DisplayName("accettaInvito (RF-GNF.02)")
    class Accetta {

        @Test
        @DisplayName("Richiesta inesistente: errore e nessun utente modificato")
        void inesistente() {
            when(richiestaRepository.findById(5L)).thenReturn(Optional.empty());

            Exception e = assertThrows(Exception.class, () -> service.accettaInvito(5L, CF_INVITATO));
            assertEquals("Richiesta non trovata", e.getMessage());
            verifyNoInteractions(urRepository);
        }

        @Test
        @DisplayName("Richiesta già accettata o rifiutata: non può essere accettata di nuovo")
        void nonPending() {
            UREntity invitato = UREntity.builder().id(2L).codiceFiscale(CF_INVITATO).build();
            RichiestaAccessoEntity giaAccettata = RichiestaAccessoEntity.builder().id(5L).stato("ACCEPTED").utenteInvitato(invitato).build();
            when(richiestaRepository.findById(5L)).thenReturn(Optional.of(giaAccettata));

            Exception e = assertThrows(Exception.class, () -> service.accettaInvito(5L, CF_INVITATO));
            assertEquals("Richiesta non valida", e.getMessage());
            verifyNoInteractions(urRepository);
            verify(richiestaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sicurezza: un utente diverso dall'invitato non può accettare l'invito")
        void altroUtente() {
            UREntity invitato = UREntity.builder().id(2L).codiceFiscale(CF_INVITATO).build();
            RichiestaAccessoEntity richiesta = RichiestaAccessoEntity.builder()
                    .id(5L).stato("PENDING").nucleo(nucleo).utenteInvitato(invitato).build();
            when(richiestaRepository.findById(5L)).thenReturn(Optional.of(richiesta));

            Exception e = assertThrows(Exception.class, () -> service.accettaInvito(5L, "BNCGVN90C03F839Z"));

            assertEquals("Non puoi accettare un invito destinato a un altro utente.", e.getMessage());
            assertNull(invitato.getNucleoFamiliare());
            assertEquals("PENDING", richiesta.getStato());
            verifyNoInteractions(urRepository);
            verify(richiestaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Richiesta PENDING accettata dall'invitato: entra nel nucleo e la richiesta diventa ACCEPTED")
        void successo() throws Exception {
            UREntity invitato = UREntity.builder().id(2L).codiceFiscale(CF_INVITATO).build();
            RichiestaAccessoEntity richiesta = RichiestaAccessoEntity.builder()
                    .id(5L).stato("PENDING").nucleo(nucleo).utenteInvitato(invitato).build();
            when(richiestaRepository.findById(5L)).thenReturn(Optional.of(richiesta));

            service.accettaInvito(5L, CF_INVITATO);

            assertSame(nucleo, invitato.getNucleoFamiliare());
            assertEquals("ACCEPTED", richiesta.getStato());
            verify(urRepository).save(invitato);
            verify(richiestaRepository).save(richiesta);
        }
    }

    @Nested
    @DisplayName("abbandonaNucleo (RF-GNF.04) e richieste pendenti (RF-GNF.06)")
    class Abbandona {

        @Test
        @DisplayName("Utente senza nucleo: errore e nessun salvataggio")
        void senzaNucleo() {
            when(urRepository.findByCodiceFiscale(CF_INVITATO))
                    .thenReturn(UREntity.builder().codiceFiscale(CF_INVITATO).build());

            Exception e = assertThrows(Exception.class, () -> service.abbandonaNucleo(CF_INVITATO));
            assertEquals("Non sei in nessun nucleo.", e.getMessage());
            verify(urRepository, never()).save(any());
        }

        @Test
        @DisplayName("Utente in un nucleo: viene scollegato e salvato")
        void successo() throws Exception {
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);

            service.abbandonaNucleo(CF_ADMIN);

            assertNull(admin.getNucleoFamiliare());
            verify(urRepository).save(admin);
        }

        @Test
        @DisplayName("Richieste pendenti: cerca solo quelle PENDING dell'utente")
        void richiestePendenti() {
            UREntity invitato = UREntity.builder().id(2L).codiceFiscale(CF_INVITATO).build();
            List<RichiestaAccessoEntity> attese = List.of(RichiestaAccessoEntity.builder().id(5L).stato("PENDING").build());
            when(urRepository.findByCodiceFiscale(CF_INVITATO)).thenReturn(invitato);
            when(richiestaRepository.findByUtenteInvitatoIdAndStato(2L, "PENDING")).thenReturn(attese);

            assertSame(attese, service.getRichiestePendenti(CF_INVITATO));
        }
    }

    @Nested
    @DisplayName("visualizzaNucleo (RF-GNF.07) e getAppoggi")
    class Visualizza {

        @Test
        @DisplayName("Richiedente non trovato tra i membri: errore")
        void richiedenteInesistente() {
            when(membroRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(Optional.empty());

            RuntimeException e = assertThrows(RuntimeException.class, () -> service.visualizzaNucleo(CF_ADMIN));
            assertEquals("Utente non trovato nel sistema.", e.getMessage());
        }

        @Test
        @DisplayName("Richiedente senza nucleo: errore")
        void senzaNucleo() {
            when(membroRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(Optional.of(MembroEntity.builder().build()));

            RuntimeException e = assertThrows(RuntimeException.class, () -> service.visualizzaNucleo(CF_ADMIN));
            assertEquals("L'utente non appartiene ad alcun nucleo familiare.", e.getMessage());
            verify(membroRepository, never()).findByNucleoFamiliare(any());
        }

        @Test
        @DisplayName("Richiedente con nucleo: restituisce i membri di quel nucleo")
        void successo() {
            List<MembroEntity> membri = List.of(MembroEntity.builder().nome("Anna").build());
            when(membroRepository.findByCodiceFiscale(CF_ADMIN))
                    .thenReturn(Optional.of(MembroEntity.builder().nucleoFamiliare(nucleo).build()));
            when(membroRepository.findByNucleoFamiliare(nucleo)).thenReturn(membri);

            assertSame(membri, service.visualizzaNucleo(CF_ADMIN));
        }

        @Test
        @DisplayName("Appoggi: utente inesistente o senza nucleo rifiutato")
        void appoggiNonAutorizzato() {
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(null);
            Exception e1 = assertThrows(Exception.class, () -> service.getAppoggi(CF_ADMIN));
            assertEquals("Utente non autorizzato o nucleo inesistente.", e1.getMessage());

            when(urRepository.findByCodiceFiscale(CF_INVITATO)).thenReturn(UREntity.builder().build());
            Exception e2 = assertThrows(Exception.class, () -> service.getAppoggi(CF_INVITATO));
            assertEquals("Utente non autorizzato o nucleo inesistente.", e2.getMessage());
        }

        @Test
        @DisplayName("Appoggi: restituisce quelli del nucleo dell'utente")
        void appoggi() throws Exception {
            List<AppoggioEntity> appoggi = List.of(AppoggioEntity.builder().id(3L).build());
            nucleo.setAppoggi(appoggi);
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);

            assertSame(appoggi, service.getAppoggi(CF_ADMIN));
        }
    }

    @Nested
    @DisplayName("modificaResidenza (RF-GNF.23)")
    class ModificaResidenza {

        private ResidenzaEntity nuova() {
            return ResidenzaEntity.builder()
                    .viaPiazza("Via Toledo").civico("5").comune("Napoli").cap("80134")
                    .provincia("Napoli").regione("Campania").paese("Italia").build();
        }

        @Test
        @DisplayName("Utente inesistente, senza nucleo o non admin: residenza non modificata")
        void nonAutorizzato() {
            when(urRepository.findByCodiceFiscale("CF_NULL")).thenReturn(null);
            Exception e1 = assertThrows(Exception.class, () -> service.modificaResidenza("CF_NULL", nuova()));
            assertEquals("Utente non trovato.", e1.getMessage());

            when(urRepository.findByCodiceFiscale("CF_NO_NUCLEO")).thenReturn(UREntity.builder().id(3L).build());
            Exception e2 = assertThrows(Exception.class, () -> service.modificaResidenza("CF_NO_NUCLEO", nuova()));
            assertEquals("Utente non associato a nessun nucleo.", e2.getMessage());

            UREntity membro = UREntity.builder().id(2L).nucleoFamiliare(nucleo).build();
            when(urRepository.findByCodiceFiscale(CF_INVITATO)).thenReturn(membro);
            Exception e3 = assertThrows(Exception.class, () -> service.modificaResidenza(CF_INVITATO, nuova()));
            assertEquals("Permessi insufficienti: devi essere amministratore del nucleo.", e3.getMessage());

            verifyNoInteractions(residenzaRepository);
        }

        @Test
        @DisplayName("Dati non validi: residenza non modificata")
        void datiNonValidi() {
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);
            ResidenzaEntity nonValida = nuova();
            nonValida.setCap("ABC");

            Exception e = assertThrows(Exception.class, () -> service.modificaResidenza(CF_ADMIN, nonValida));
            assertEquals("Dati residenza non validi.", e.getMessage());
            verifyNoInteractions(residenzaRepository);
        }

        @Test
        @DisplayName("Admin con dati validi: aggiorna tutti i campi della residenza attuale")
        void successo() throws Exception {
            ResidenzaEntity attuale = ResidenzaEntity.builder().id(7L)
                    .viaPiazza("Via Roma").civico("1").comune("Pompei").cap("80045")
                    .provincia("Napoli").regione("Campania").paese("Italia").build();
            nucleo.setResidenza(attuale);
            when(urRepository.findByCodiceFiscale(CF_ADMIN)).thenReturn(admin);
            ResidenzaEntity nuova = nuova();
            nuova.setProvincia("Salerno");

            service.modificaResidenza(CF_ADMIN, nuova);

            verify(residenzaRepository).save(attuale);
            assertAll(
                    () -> assertEquals(7L, attuale.getId(), "si aggiorna la residenza esistente, non se ne crea una nuova"),
                    () -> assertEquals("Via Toledo", attuale.getViaPiazza()),
                    () -> assertEquals("5", attuale.getCivico()),
                    () -> assertEquals("Napoli", attuale.getComune()),
                    () -> assertEquals("80134", attuale.getCap()),
                    () -> assertEquals("Salerno", attuale.getProvincia()),
                    () -> assertEquals("Campania", attuale.getRegione()),
                    () -> assertEquals("Italia", attuale.getPaese()));
        }
    }
}
