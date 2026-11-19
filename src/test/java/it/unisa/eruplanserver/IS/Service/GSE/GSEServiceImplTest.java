package it.unisa.eruplanserver.IS.Service.GSE;

import it.unisa.eruplanserver.IS.Entity.GSE.EruzioneVulcanicaEntity;
import it.unisa.eruplanserver.IS.Entity.GSE.GestioneStoricoEvacuazioniEntity;
import it.unisa.eruplanserver.IS.Exception.GSE.GSEException;
import it.unisa.eruplanserver.IS.Repository.GSE.EruzioniRepository;
import it.unisa.eruplanserver.IS.Repository.GSE.GSERepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test per {@link GSEServiceImpl}: storico delle evacuazioni ed elenco delle eruzioni.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GSEServiceImpl - storico evacuazioni ed eruzioni")
class GSEServiceImplTest {

    private static final String NESSUN_DATO = "Non è stato creato nessun piano di evacuazione.";
    private static final String DB_ASSENTE = "Connessione al DB assente.";

    @Mock
    private GSERepository partecipazioneRepository;

    @Mock
    private EruzioniRepository eruzioniRepository;

    @InjectMocks
    private GSEServiceImpl service;

    @Nested
    @DisplayName("getPartecipantiPiano")
    class Partecipanti {

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
        @DisplayName("ID nullo, zero o negativo: GSEException senza interrogare il database")
        void idNonValido(Long id) {
            GSEException e = assertThrows(GSEException.class, () -> service.getPartecipantiPiano(id));

            assertEquals("Errore durante il recupero dei partecipanti: ID Piano non valido.", e.getMessage());
            verifyNoInteractions(partecipazioneRepository);
        }

        @Test
        @DisplayName("ID valido: restituisce i partecipanti del piano")
        void idValido() {
            List<GestioneStoricoEvacuazioniEntity> attesi = List.of(
                    GestioneStoricoEvacuazioniEntity.builder().id(1L).isSalvo(true).build(),
                    GestioneStoricoEvacuazioniEntity.builder().id(2L).isSalvo(false).build());
            when(partecipazioneRepository.findByPianoId(5L)).thenReturn(attesi);

            assertSame(attesi, service.getPartecipantiPiano(5L));
        }

        @Test
        @DisplayName("Piano senza partecipanti: lista vuota, non un errore")
        void nessunPartecipante() {
            when(partecipazioneRepository.findByPianoId(5L)).thenReturn(Collections.emptyList());

            assertTrue(service.getPartecipantiPiano(5L).isEmpty());
        }

        @Test
        @DisplayName("Errore del database: incapsulato in GSEException con il messaggio originale")
        void erroreDb() {
            when(partecipazioneRepository.findByPianoId(5L))
                    .thenThrow(new DataAccessResourceFailureException("timeout"));

            GSEException e = assertThrows(GSEException.class, () -> service.getPartecipantiPiano(5L));
            assertEquals("Errore durante il recupero dei partecipanti: timeout", e.getMessage());
        }
    }

    @Nested
    @DisplayName("liste delle eruzioni (normale, crescente, decrescente)")
    class Eruzioni {

        /** Ogni caso: nome, metodo del repository da simulare, metodo del service da chiamare. */
        static Stream<Arguments> varianti() {
            return Stream.of(
                    Arguments.of("getListaEruzioni",
                            (Function<EruzioniRepository, List<EruzioneVulcanicaEntity>>) EruzioniRepository::findAll,
                            (Function<GSEServiceImpl, List<EruzioneVulcanicaEntity>>) GSEServiceImpl::getListaEruzioni),
                    Arguments.of("getListaEruzioniCrescente",
                            (Function<EruzioniRepository, List<EruzioneVulcanicaEntity>>) EruzioniRepository::findAllByOrderByDataInizioAsc,
                            (Function<GSEServiceImpl, List<EruzioneVulcanicaEntity>>) GSEServiceImpl::getListaEruzioniCrescente),
                    Arguments.of("getListaEruzioniDecrescente",
                            (Function<EruzioniRepository, List<EruzioneVulcanicaEntity>>) EruzioniRepository::findAllByOrderByDataInizioDesc,
                            (Function<GSEServiceImpl, List<EruzioneVulcanicaEntity>>) GSEServiceImpl::getListaEruzioniDecrescente));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("varianti")
        @DisplayName("Con dati: restituisce la lista del repository così com'è")
        void conDati(String nome,
                     Function<EruzioniRepository, List<EruzioneVulcanicaEntity>> query,
                     Function<GSEServiceImpl, List<EruzioneVulcanicaEntity>> chiamata) {
            List<EruzioneVulcanicaEntity> attese = List.of(
                    eruzione("Vesuvio 1944", LocalDate.of(1944, 3, 18)),
                    eruzione("Vesuvio 1906", LocalDate.of(1906, 4, 4)));
            when(query.apply(eruzioniRepository)).thenReturn(attese);

            assertSame(attese, chiamata.apply(service));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("varianti")
        @DisplayName("Lista vuota: GSEException con messaggio dedicato")
        void vuota(String nome,
                   Function<EruzioniRepository, List<EruzioneVulcanicaEntity>> query,
                   Function<GSEServiceImpl, List<EruzioneVulcanicaEntity>> chiamata) {
            when(query.apply(eruzioniRepository)).thenReturn(Collections.emptyList());

            assertMessaggio(NESSUN_DATO, () -> chiamata.apply(service));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("varianti")
        @DisplayName("Errore del database: GSEException 'Connessione al DB assente.'")
        void erroreDb(String nome,
                      Function<EruzioniRepository, List<EruzioneVulcanicaEntity>> query,
                      Function<GSEServiceImpl, List<EruzioneVulcanicaEntity>> chiamata) {
            when(query.apply(eruzioniRepository)).thenThrow(new DataAccessResourceFailureException("down"));

            assertMessaggio(DB_ASSENTE, () -> chiamata.apply(service));
        }

        private void assertMessaggio(String atteso, Executable chiamata) {
            GSEException e = assertThrows(GSEException.class, chiamata);
            assertEquals(atteso, e.getMessage());
        }

        private EruzioneVulcanicaEntity eruzione(String nome, LocalDate inizio) {
            return EruzioneVulcanicaEntity.builder().nomeEvento(nome).vulcanoNome("Vesuvio").dataInizio(inizio).build();
        }
    }
}
