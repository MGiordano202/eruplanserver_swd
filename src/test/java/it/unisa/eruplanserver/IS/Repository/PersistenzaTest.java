package it.unisa.eruplanserver.IS.Repository;

import it.unisa.eruplanserver.IS.Entity.GNF.MembroEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.NucleoFamiliareEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.RichiestaAccessoEntity;
import it.unisa.eruplanserver.IS.Entity.GPE.PianoEvacuazioneEntity;
import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaPericolo;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaSicura;
import it.unisa.eruplanserver.IS.Entity.GSE.EruzioneVulcanicaEntity;
import it.unisa.eruplanserver.IS.Entity.GSE.GestioneStoricoEvacuazioniEntity;
import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Entity.GUW.OPCEntity;
import it.unisa.eruplanserver.IS.Repository.GE.GestioneEvacuazione;
import it.unisa.eruplanserver.IS.Repository.GNF.MembroRepository;
import it.unisa.eruplanserver.IS.Repository.GNF.RichiestaAccessoRepository;
import it.unisa.eruplanserver.IS.Repository.GPE.GPERepository;
import it.unisa.eruplanserver.IS.Repository.GSE.EruzioniRepository;
import it.unisa.eruplanserver.IS.Repository.GSE.GSERepository;
import it.unisa.eruplanserver.IS.Repository.GUM.URRepository;
import it.unisa.eruplanserver.IS.Repository.GUW.OPCRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di integrazione service ↔ database: entity, converter JSON e query dei repository
 * eseguiti da Hibernate su un database H2 in memoria.
 * Ogni test gira in una transazione annullata alla fine, quindi i test non si influenzano.
 */
@DataJpaTest
@DisplayName("Persistenza - entity, converter e query dei repository su database reale (H2)")
class PersistenzaTest {

    @Autowired private TestEntityManager em;
    @Autowired private URRepository urRepository;
    @Autowired private OPCRepository opcRepository;
    @Autowired private GestioneEvacuazione geRepository;
    @Autowired private GPERepository gpeRepository;
    @Autowired private GSERepository gseRepository;
    @Autowired private EruzioniRepository eruzioniRepository;
    @Autowired private RichiestaAccessoRepository richiestaRepository;
    @Autowired private MembroRepository membroRepository;

    private UREntity utente(String cf) {
        return em.persist(UREntity.builder().nome("Mario").cognome("Rossi").codiceFiscale(cf)
                .password("hash").sesso("M").dataDiNascita(LocalDate.of(1980, 1, 1)).build());
    }

    private NucleoFamiliareEntity nucleo(UREntity admin) {
        return em.persist(NucleoFamiliareEntity.builder().admin(admin).hasVeicolo(false).numeroPostiVeicolo(0).build());
    }

    private PianoEvacuazioneEntity piano(String nome, ZonaPericolo pericolo, ZonaSicura sicura) {
        return em.persist(PianoEvacuazioneEntity.builder().nome(nome).dataCreazione(LocalDateTime.of(2026, 1, 1, 10, 0))
                .stato("ATTIVO").zonaPericolo(pericolo).zoneSicure(sicura).build());
    }

    /** Scrive su database e svuota la cache di Hibernate: la lettura successiva arriva davvero dal database. */
    private void flushEPulisci() {
        em.flush();
        em.clear();
    }

    @Nested
    @DisplayName("Piano di evacuazione (GPE)")
    class Piano {

        @Test
        @DisplayName("Le zone salvate come JSON vengono rilette identiche dal database")
        void zoneSalvateERilette() {
            List<Punto> punti = List.of(new Punto(40.82, 14.42), new Punto(40.83, 14.43), new Punto(40.81, 14.44), new Punto(40.82, 14.42));
            ZonaPericolo pericolo = new ZonaPericolo();
            pericolo.setPunti(punti);
            Long id = piano("Piano Vesuvio", pericolo, new ZonaSicura(new Punto(40.85, 14.26), 120)).getId();
            flushEPulisci();

            PianoEvacuazioneEntity letto = gpeRepository.findById(id).orElseThrow();

            assertAll(
                    () -> assertEquals("Piano Vesuvio", letto.getNome()),
                    () -> assertEquals("ATTIVO", letto.getStato()),
                    () -> assertEquals(punti, letto.getZonaPericolo().getPunti()),
                    () -> assertEquals(new Punto(40.85, 14.26), letto.getZoneSicure().getCoordinate()),
                    () -> assertEquals(120, letto.getZoneSicure().getRaggio()));
        }

        @Test
        @DisplayName("Piano senza zone: salvato con zone null")
        void senzaZone() {
            Long id = piano("Piano A", null, null).getId();
            flushEPulisci();

            PianoEvacuazioneEntity letto = gpeRepository.findById(id).orElseThrow();
            assertNull(letto.getZonaPericolo());
            assertNull(letto.getZoneSicure());
        }

        @Test
        @DisplayName("existsByNome distingue i nomi esistenti da quelli nuovi")
        void existsByNome() {
            piano("Piano Vesuvio", null, null);
            flushEPulisci();

            assertTrue(gpeRepository.existsByNome("Piano Vesuvio"));
            assertFalse(gpeRepository.existsByNome("Piano Etna"));
        }

        @Test
        @DisplayName("Un nome oltre i 35 caratteri della colonna viene rifiutato dal database")
        void nomeTroppoLungo() {
            // Con id IDENTITY l'INSERT parte già al persist: l'errore arriva lì, non al flush
            assertThrows(DataException.class, () -> piano("a".repeat(36), null, null));
        }
    }

    @Nested
    @DisplayName("Storico ed eruzioni (GSE)")
    class Storico {

        @Test
        @DisplayName("Eruzioni ordinate per data di inizio, crescente e decrescente")
        void ordinamento() {
            em.persist(EruzioneVulcanicaEntity.builder().nomeEvento("1944").vulcanoNome("Vesuvio").dataInizio(LocalDate.of(1944, 3, 18)).build());
            em.persist(EruzioneVulcanicaEntity.builder().nomeEvento("1631").vulcanoNome("Vesuvio").dataInizio(LocalDate.of(1631, 12, 16)).build());
            em.persist(EruzioneVulcanicaEntity.builder().nomeEvento("1906").vulcanoNome("Vesuvio").dataInizio(LocalDate.of(1906, 4, 4)).build());
            flushEPulisci();

            assertEquals(List.of("1631", "1906", "1944"),
                    eruzioniRepository.findAllByOrderByDataInizioAsc().stream().map(EruzioneVulcanicaEntity::getNomeEvento).toList());
            assertEquals(List.of("1944", "1906", "1631"),
                    eruzioniRepository.findAllByOrderByDataInizioDesc().stream().map(EruzioneVulcanicaEntity::getNomeEvento).toList());
        }

        @Test
        @DisplayName("findByPianoId restituisce solo i partecipanti di quel piano")
        void partecipantiPerPiano() {
            PianoEvacuazioneEntity pianoA = piano("Piano A", null, null);
            PianoEvacuazioneEntity pianoB = piano("Piano B", null, null);
            UREntity mario = utente("RSSMRA80A01H703X");
            UREntity luigi = utente("VRDLGU75B02F839K");
            em.persist(GestioneStoricoEvacuazioniEntity.builder().piano(pianoA).cittadino(mario).isSalvo(true).build());
            em.persist(GestioneStoricoEvacuazioniEntity.builder().piano(pianoA).cittadino(luigi).isSalvo(false).build());
            em.persist(GestioneStoricoEvacuazioniEntity.builder().piano(pianoB).cittadino(mario).isSalvo(true).build());
            flushEPulisci();

            List<GestioneStoricoEvacuazioniEntity> partecipanti = gseRepository.findByPianoId(pianoA.getId());

            assertEquals(2, partecipanti.size());
            assertTrue(partecipanti.stream().allMatch(p -> p.getPiano().getId().equals(pianoA.getId())));
            assertTrue(gseRepository.findByPianoId(999L).isEmpty());
        }
    }

    @Nested
    @DisplayName("Utenti, operatori e nucleo (GUM, GUW, GE, GNF)")
    class Utenti {

        @Test
        @DisplayName("Ricerca utente per CF: trovato, inesistente, existsBy")
        void utentePerCf() {
            utente("RSSMRA80A01H703X");
            flushEPulisci();

            assertEquals("Mario", urRepository.findByCodiceFiscale("RSSMRA80A01H703X").getNome());
            assertNull(urRepository.findByCodiceFiscale("XXXXXX00X00X000X"), "il service si aspetta null, non un'eccezione");
            assertTrue(urRepository.existsByCodiceFiscale("RSSMRA80A01H703X"));
            assertFalse(urRepository.existsByCodiceFiscale("XXXXXX00X00X000X"));
            assertNotNull(geRepository.findByCodiceFiscale("RSSMRA80A01H703X"), "GE usa la stessa tabella degli utenti");
        }

        @Test
        @DisplayName("Il codice fiscale è unico: un secondo utente con lo stesso CF viene rifiutato")
        void cfUnico() {
            utente("RSSMRA80A01H703X");

            assertThrows(ConstraintViolationException.class, () -> utente("RSSMRA80A01H703X"));
        }

        @Test
        @DisplayName("Ricerca operatore per CF")
        void operatorePerCf() {
            em.persist(OPCEntity.builder().codiceFiscale("VRDLGU75B02F839K").password("hash").nome("Luigi").build());
            flushEPulisci();

            assertEquals("Luigi", opcRepository.findByCodiceFiscale("VRDLGU75B02F839K").getNome());
            assertNull(opcRepository.findByCodiceFiscale("RSSMRA80A01H703X"));
        }

        @Test
        @DisplayName("Richieste pendenti: solo quelle PENDING dell'utente invitato")
        void richiestePendenti() {
            UREntity admin = utente("RSSMRA80A01H703X");
            UREntity invitato = utente("VRDLGU75B02F839K");
            UREntity altro = utente("BNCGVN90C03F839Z");
            NucleoFamiliareEntity n = nucleo(admin);
            em.persist(RichiestaAccessoEntity.builder().nucleo(n).utenteInvitato(invitato).stato("PENDING").build());
            em.persist(RichiestaAccessoEntity.builder().nucleo(n).utenteInvitato(invitato).stato("ACCEPTED").build());
            em.persist(RichiestaAccessoEntity.builder().nucleo(n).utenteInvitato(altro).stato("PENDING").build());
            flushEPulisci();

            List<RichiestaAccessoEntity> pendenti = richiestaRepository.findByUtenteInvitatoIdAndStato(invitato.getId(), "PENDING");

            assertEquals(1, pendenti.size());
            assertEquals(invitato.getId(), pendenti.get(0).getUtenteInvitato().getId());
        }

        @Test
        @DisplayName("Membri: ricerca per nucleo e per CF")
        void membri() {
            NucleoFamiliareEntity mio = nucleo(utente("RSSMRA80A01H703X"));
            NucleoFamiliareEntity altro = nucleo(utente("VRDLGU75B02F839K"));
            em.persist(MembroEntity.builder().nome("Anna").cognome("Rossi").codiceFiscale("RSSNNA10A41F839X").nucleoFamiliare(mio).build());
            em.persist(MembroEntity.builder().nome("Luca").cognome("Rossi").nucleoFamiliare(mio).build());
            em.persist(MembroEntity.builder().nome("Sara").cognome("Verdi").nucleoFamiliare(altro).build());
            flushEPulisci();

            assertEquals(2, membroRepository.findByNucleoFamiliare(mio).size());
            assertEquals("Anna", membroRepository.findByCodiceFiscale("RSSNNA10A41F839X").orElseThrow().getNome());
            assertTrue(membroRepository.findByCodiceFiscale("XXXXXX00X00X000X").isEmpty());
        }

        @Test
        @DisplayName("Nucleo senza veicolo (0 posti) salvato e riletto")
        void nucleoSenzaVeicolo() {
            Long id = nucleo(utente("RSSMRA80A01H703X")).getId();
            flushEPulisci();

            NucleoFamiliareEntity letto = em.find(NucleoFamiliareEntity.class, id);
            assertFalse(letto.isHasVeicolo());
            assertEquals(0, letto.getNumeroPostiVeicolo());
        }
    }
}
