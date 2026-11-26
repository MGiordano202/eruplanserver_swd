package it.unisa.eruplanserver.IS.Control.GNF;

import it.unisa.eruplanserver.IS.Entity.GNF.AppoggioEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.MembroEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.NucleoFamiliareEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.ResidenzaEntity;
import it.unisa.eruplanserver.IS.Entity.GNF.RichiestaAccessoEntity;
import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Service.GNF.GNFServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test di integrazione HTTP → GNFControl → GNFServiceImpl (mockato): gestione del nucleo familiare.
 * Verificano URL, lettura di body/parametri, controllo della sessione e codici HTTP.
 */
@WebMvcTest(GNFControl.class)
@DisplayName("GNFControl - gestione del nucleo familiare via HTTP")
class GNFControlTest {

    private static final String CF = "RSSMRA85M01H501U";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GNFServiceImpl gnfService;

    private static MockHttpSession loggato() {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("codiceFiscale", CF);
        return s;
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,    /gestoreNucleo/membri,",
            "POST,   /gestoreNucleo/invita?cfInvitato=VRDLGU75B12F839K,",
            "POST,   /gestoreNucleo/accetta/5,",
            "POST,   /gestoreNucleo/aggiungiMembro,  {}",
            "POST,   /gestoreNucleo/abbandona,",
            "GET,    /gestoreNucleo/richieste,",
            "POST,   /gestoreNucleo/appoggi/aggiungi, {}",
            "GET,    /gestoreNucleo/appoggi,",
            "DELETE, /gestoreNucleo/appoggi/rimuovi/1,",
            "POST,   /gestoreNucleo/crea,             {}",
            "POST,   /gestoreNucleo/residenza/modifica, {}"
    })
    @DisplayName("Senza login: 401 e service non chiamato")
    void senzaLogin(String metodo, String url, String body) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), url).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(gnfService);
    }

    @Nested
    @DisplayName("Membri e inviti")
    class MembriInviti {

        @Test
        @DisplayName("GET /membri: 200 con i membri del nucleo")
        void membri() throws Exception {
            when(gnfService.visualizzaNucleo(CF)).thenReturn(List.of(
                    MembroEntity.builder().nome("Anna").cognome("Rossi").build(),
                    MembroEntity.builder().nome("Luca").cognome("Rossi").build()));

            mvc.perform(get("/gestoreNucleo/membri").session(loggato()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].nome").value("Anna"));
        }

        @Test
        @DisplayName("GET /membri: errore del service → 400 con il messaggio")
        void membriErrore() throws Exception {
            when(gnfService.visualizzaNucleo(CF)).thenThrow(new RuntimeException("L'utente non appartiene ad alcun nucleo familiare."));

            mvc.perform(get("/gestoreNucleo/membri").session(loggato()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("L'utente non appartiene ad alcun nucleo familiare."));
        }

        @Test
        @DisplayName("POST /invita: CF valido passato come parametro → 200")
        void invita() throws Exception {
            mvc.perform(post("/gestoreNucleo/invita").param("cfInvitato", "VRDLGU75B12F839K").session(loggato()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Invito inviato con successo."));
            verify(gnfService).invitaUtente(CF, "VRDLGU75B12F839K");
        }

        @Test
        @DisplayName("POST /invita senza parametro cfInvitato: 400 e service non chiamato")
        void invitaSenzaParametro() throws Exception {
            mvc.perform(post("/gestoreNucleo/invita").session(loggato()))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(gnfService);
        }

        @Test
        @DisplayName("POST /accetta/{id}: passa al service l'utente loggato; 200 se accetta, 400 con il messaggio se rifiuta")
        void accetta() throws Exception {
            mvc.perform(post("/gestoreNucleo/accetta/5").session(loggato()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Benvenuto nel nucleo!"));
            verify(gnfService).accettaInvito(5L, CF);

            doThrow(new Exception("Richiesta non valida")).when(gnfService).accettaInvito(6L, CF);
            mvc.perform(post("/gestoreNucleo/accetta/6").session(loggato()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Richiesta non valida"));
        }

        @Test
        @DisplayName("POST /aggiungiMembro: il JSON diventa un MembroEntity passato al service")
        void aggiungiMembro() throws Exception {
            String body = """
                    {"nome":"Anna","cognome":"Rossi","codiceFiscale":"RSSNNA10A41F839X",
                     "dataDiNascita":"01/01/2010","sesso":"F","assistenza":false,"minorenne":true}""";

            mvc.perform(post("/gestoreNucleo/aggiungiMembro").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Membro aggiunto al nucleo."));

            ArgumentCaptor<MembroEntity> captor = ArgumentCaptor.forClass(MembroEntity.class);
            verify(gnfService).aggiungiMembroManuale(eq(CF), captor.capture());
            assertAll(
                    () -> assertEquals("Anna", captor.getValue().getNome()),
                    () -> assertEquals("01/01/2010", captor.getValue().getDataDiNascita()),
                    () -> assertTrue(captor.getValue().getMinorenne()),
                    () -> assertFalse(captor.getValue().getAssistenza()));
        }

        @Test
        @DisplayName("POST /aggiungiMembro: dati rifiutati → 400 con il messaggio")
        void aggiungiMembroRifiutato() throws Exception {
            doThrow(new Exception("Nome troppo corto")).when(gnfService).aggiungiMembroManuale(eq(CF), any());

            mvc.perform(post("/gestoreNucleo/aggiungiMembro").session(loggato()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Nome troppo corto"));
        }

        @Test
        @DisplayName("POST /abbandona e GET /richieste: 200")
        void abbandonaERichieste() throws Exception {
            mvc.perform(post("/gestoreNucleo/abbandona").session(loggato()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Hai abbandonato il nucleo."));
            verify(gnfService).abbandonaNucleo(CF);

            when(gnfService.getRichiestePendenti(CF)).thenReturn(List.of(RichiestaAccessoEntity.builder().id(5L).stato("PENDING").build()));
            mvc.perform(get("/gestoreNucleo/richieste").session(loggato()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].stato").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("Appoggi")
    class Appoggi {

        @Test
        @DisplayName("POST /appoggi/aggiungi: 200, oppure 400 con il messaggio del service")
        void aggiungi() throws Exception {
            String body = "{\"viaPiazza\":\"Via Sarti\",\"civico\":\"675\",\"cap\":\"67489\",\"comune\":\"Pompei\","
                    + "\"provincia\":\"Napoli\",\"regione\":\"Campania\",\"paese\":\"Italia\"}";
            mvc.perform(post("/gestoreNucleo/appoggi/aggiungi").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Appoggio aggiunto con successo."));

            doThrow(new Exception("Dati indirizzo non validi.")).when(gnfService).aggiungiAppoggio(eq(CF), any());
            mvc.perform(post("/gestoreNucleo/appoggi/aggiungi").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Dati indirizzo non validi."));
        }

        @Test
        @DisplayName("GET /appoggi: 200 con la lista, 400 se il service rifiuta")
        void lista() throws Exception {
            when(gnfService.getAppoggi(CF)).thenReturn(List.of(AppoggioEntity.builder().id(3L).comune("Pompei").build()));
            mvc.perform(get("/gestoreNucleo/appoggi").session(loggato()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].comune").value("Pompei"));

            when(gnfService.getAppoggi(CF)).thenThrow(new Exception("Utente non autorizzato o nucleo inesistente."));
            mvc.perform(get("/gestoreNucleo/appoggi").session(loggato()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /appoggi/rimuovi/{id}: 200 con id valido")
        void rimuovi() throws Exception {
            mvc.perform(delete("/gestoreNucleo/appoggi/rimuovi/3").session(loggato()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Appoggio rimosso con successo."));
            verify(gnfService).rimuoviAppoggio(CF, 3L);
        }

        @ParameterizedTest(name = "id = {0}")
        @CsvSource({"0", "-1"})
        @DisplayName("DELETE /appoggi/rimuovi/{id}: id zero o negativo → 400 e service non chiamato")
        void rimuoviIdNonValido(String id) throws Exception {
            mvc.perform(delete("/gestoreNucleo/appoggi/rimuovi/" + id).session(loggato()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("ID appoggio non valido."));
            verifyNoInteractions(gnfService);
        }

        @Test
        @DisplayName("DELETE /appoggi/rimuovi/11111f1 (TC-M-10.3 via HTTP): id non numerico → 400 e service non chiamato")
        void rimuoviIdNonNumerico() throws Exception {
            mvc.perform(delete("/gestoreNucleo/appoggi/rimuovi/11111f1").session(loggato()))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(gnfService);
        }
    }

    @Nested
    @DisplayName("Creazione nucleo, veicolo e residenza")
    class Nucleo {

        private static final String CREA = """
                {"residenza":{"viaPiazza":"Via Roma","civico":"10","comune":"Napoli","cap":"80100",
                              "provincia":"Napoli","regione":"Campania","paese":"Italia"},
                 "hasVeicolo":true,"numeroPostiVeicolo":5}""";

        @Test
        @DisplayName("POST /crea: il JSON diventa residenza e dati veicolo passati al service")
        void creaLetturaBody() throws Exception {
            when(gnfService.creaNucleoFamiliare(eq(CF), any(), anyBoolean(), any()))
                    .thenReturn(NucleoFamiliareEntity.builder().id(10L).hasVeicolo(true).numeroPostiVeicolo(5).build());

            mvc.perform(post("/gestoreNucleo/crea").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(CREA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));

            ArgumentCaptor<ResidenzaEntity> residenza = ArgumentCaptor.forClass(ResidenzaEntity.class);
            verify(gnfService).creaNucleoFamiliare(eq(CF), residenza.capture(), eq(true), eq(5));
            assertEquals("Via Roma", residenza.getValue().getViaPiazza());
            assertEquals("80100", residenza.getValue().getCap());
        }

        @Test
        @DisplayName("POST /crea: risposta con il nucleo creato come lo restituisce davvero il service (admin ↔ nucleo)")
        void creaRispostaReale() throws Exception {
            // creaNucleoFamiliare collega admin e nucleo in entrambe le direzioni:
            // nucleo.admin = utente e utente.nucleoFamiliare = nucleo
            UREntity admin = UREntity.builder().id(1L).codiceFiscale(CF).nome("Mario").build();
            NucleoFamiliareEntity nucleo = NucleoFamiliareEntity.builder().id(10L).admin(admin).hasVeicolo(true).numeroPostiVeicolo(5).build();
            admin.setNucleoFamiliare(nucleo);
            when(gnfService.creaNucleoFamiliare(eq(CF), any(), anyBoolean(), any())).thenReturn(nucleo);

            mvc.perform(post("/gestoreNucleo/crea").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(CREA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.admin.nome").value("Mario"))
                    .andExpect(jsonPath("$.admin.nucleoFamiliare").doesNotExist());
        }

        @Test
        @DisplayName("Sicurezza: l'hash della password dell'admin non compare nella risposta di /crea")
        void creaSenzaPassword() throws Exception {
            UREntity admin = UREntity.builder().id(1L).codiceFiscale(CF).nome("Mario").password("hash-segreto").build();
            when(gnfService.creaNucleoFamiliare(eq(CF), any(), anyBoolean(), any()))
                    .thenReturn(NucleoFamiliareEntity.builder().id(10L).admin(admin).build());

            mvc.perform(post("/gestoreNucleo/crea").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(CREA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.admin.password").doesNotExist())
                    .andExpect(content().string(not(containsString("hash-segreto"))));
        }

        @Test
        @DisplayName("POST /crea: dati rifiutati → 400 con il messaggio")
        void creaRifiutato() throws Exception {
            when(gnfService.creaNucleoFamiliare(eq(CF), any(), anyBoolean(), any()))
                    .thenThrow(new Exception("l'utente fa già parte di un nucleo familiare."));

            mvc.perform(post("/gestoreNucleo/crea").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(CREA))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("errore. l'utente fa già parte di un nucleo familiare."));
        }

        @Test
        @DisplayName("PUT /veicolo: 200 con i dati del body, 400 senza login o se il service rifiuta")
        void veicolo() throws Exception {
            String body = "{\"hasVeicolo\":true,\"numeroPostiVeicolo\":4}";
            mvc.perform(put("/gestoreNucleo/veicolo").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("dati aggiornati con successo."));
            verify(gnfService).aggiornaVeicolo(CF, true, 4);

            mvc.perform(put("/gestoreNucleo/veicolo").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Login richiesto"));

            doThrow(new Exception("Il numero di posti deve essere tra 1 e 8.")).when(gnfService).aggiornaVeicolo(CF, true, 9);
            mvc.perform(put("/gestoreNucleo/veicolo").session(loggato()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hasVeicolo\":true,\"numeroPostiVeicolo\":9}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("erroreIl numero di posti deve essere tra 1 e 8."));
        }

        @Test
        @DisplayName("POST /residenza/modifica: 200, oppure 400 con il messaggio del service")
        void residenza() throws Exception {
            String body = "{\"viaPiazza\":\"Via Toledo\",\"civico\":\"5\",\"comune\":\"Napoli\",\"cap\":\"80134\","
                    + "\"provincia\":\"Napoli\",\"regione\":\"Campania\",\"paese\":\"Italia\"}";
            mvc.perform(post("/gestoreNucleo/residenza/modifica").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Residenza aggiornata con successo."));

            doThrow(new Exception("Dati residenza non validi.")).when(gnfService).modificaResidenza(eq(CF), any());
            mvc.perform(post("/gestoreNucleo/residenza/modifica").session(loggato()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Dati residenza non validi."));
        }
    }
}
