package it.unisa.eruplanserver.IS.Control.GSE;

import it.unisa.eruplanserver.IS.Entity.GSE.EruzioneVulcanicaEntity;
import it.unisa.eruplanserver.IS.Entity.GSE.GestioneStoricoEvacuazioniEntity;
import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Exception.GSE.GSEException;
import it.unisa.eruplanserver.IS.Service.GSE.GSEService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test di integrazione HTTP → GSEControl → GSEService (mockato): storico evacuazioni ed eruzioni,
 * accessibili solo agli operatori.
 */
@WebMvcTest(GSEControl.class)
@DisplayName("GSEControl - storico evacuazioni ed eruzioni via HTTP")
class GSEControlTest {

    private static final String NESSUN_DATO = "Non è stato creato nessun piano di evacuazione.";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GSEService gseService;

    private static MockHttpSession operatore() {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("isOperatore", true);
        return s;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/gestoreStorico/piani/1", "/gestoreStorico/eruzioni",
            "/gestoreStorico/eruzioni/ordine/crescente", "/gestoreStorico/eruzioni/ordine/decrescente"})
    @DisplayName("Senza login da operatore: 401 e service non chiamato")
    void senzaLogin(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());

        MockHttpSession cittadino = new MockHttpSession();
        cittadino.setAttribute("isOperatore", false);
        mvc.perform(get(url).session(cittadino)).andExpect(status().isUnauthorized());

        verifyNoInteractions(gseService);
    }

    @Nested
    @DisplayName("GET /gestoreStorico/piani/{idPiano}")
    class Piani {

        @Test
        @DisplayName("Con partecipanti: 200 e lista in JSON")
        void conPartecipanti() throws Exception {
            when(gseService.getPartecipantiPiano(1L)).thenReturn(List.of(
                    GestioneStoricoEvacuazioniEntity.builder().id(1L).isSalvo(true).build(),
                    GestioneStoricoEvacuazioniEntity.builder().id(2L).isSalvo(false).build()));

            mvc.perform(get("/gestoreStorico/piani/1").session(operatore()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Sicurezza: i dati del cittadino nello storico non includono l'hash della password")
        void senzaPassword() throws Exception {
            UREntity cittadino = UREntity.builder().id(3L).nome("Mario").codiceFiscale("RSSMRA80A01H703X").password("hash-segreto").build();
            when(gseService.getPartecipantiPiano(1L)).thenReturn(List.of(
                    GestioneStoricoEvacuazioniEntity.builder().id(1L).cittadino(cittadino).isSalvo(true).build()));

            mvc.perform(get("/gestoreStorico/piani/1").session(operatore()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].cittadino.nome").value("Mario"))
                    .andExpect(jsonPath("$[0].cittadino.password").doesNotExist())
                    .andExpect(content().string(not(containsString("hash-segreto"))));
        }

        @Test
        @DisplayName("Senza partecipanti: 204")
        void vuoto() throws Exception {
            when(gseService.getPartecipantiPiano(1L)).thenReturn(Collections.emptyList());

            mvc.perform(get("/gestoreStorico/piani/1").session(operatore()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Errore del service: 500 con il messaggio")
        void errore() throws Exception {
            when(gseService.getPartecipantiPiano(-1L)).thenThrow(new GSEException("ID Piano non valido."));

            mvc.perform(get("/gestoreStorico/piani/-1").session(operatore()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("ID Piano non valido.")));
        }
    }

    @Nested
    @DisplayName("GET /gestoreStorico/eruzioni (normale, crescente, decrescente)")
    class Eruzioni {

        static Stream<Arguments> endpoint() {
            return Stream.of(
                    Arguments.of("/gestoreStorico/eruzioni", (Function<GSEService, List<EruzioneVulcanicaEntity>>) GSEService::getListaEruzioni),
                    Arguments.of("/gestoreStorico/eruzioni/ordine/crescente", (Function<GSEService, List<EruzioneVulcanicaEntity>>) GSEService::getListaEruzioniCrescente),
                    Arguments.of("/gestoreStorico/eruzioni/ordine/decrescente", (Function<GSEService, List<EruzioneVulcanicaEntity>>) GSEService::getListaEruzioniDecrescente));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("endpoint")
        @DisplayName("Con eruzioni: 200 con i dati nell'ordine del service")
        void conDati(String url, Function<GSEService, List<EruzioneVulcanicaEntity>> metodo) throws Exception {
            when(metodo.apply(gseService)).thenReturn(List.of(
                    EruzioneVulcanicaEntity.builder().nomeEvento("Vesuvio 1906").vulcanoNome("Vesuvio").dataInizio(LocalDate.of(1906, 4, 4)).build(),
                    EruzioneVulcanicaEntity.builder().nomeEvento("Vesuvio 1944").vulcanoNome("Vesuvio").dataInizio(LocalDate.of(1944, 3, 18)).build()));

            mvc.perform(get(url).session(operatore()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nomeEvento").value("Vesuvio 1906"))
                    .andExpect(jsonPath("$[1].nomeEvento").value("Vesuvio 1944"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("endpoint")
        @DisplayName("Nessuna eruzione: 204")
        void nessuna(String url, Function<GSEService, List<EruzioneVulcanicaEntity>> metodo) throws Exception {
            when(metodo.apply(gseService)).thenThrow(new GSEException(NESSUN_DATO));

            mvc.perform(get(url).session(operatore()))
                    .andExpect(status().isNoContent());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("endpoint")
        @DisplayName("Database non raggiungibile: 503")
        void dbAssente(String url, Function<GSEService, List<EruzioneVulcanicaEntity>> metodo) throws Exception {
            when(metodo.apply(gseService)).thenThrow(new GSEException("Connessione al DB assente."));

            mvc.perform(get(url).session(operatore()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string("Connessione al DB assente."));
        }
    }
}
