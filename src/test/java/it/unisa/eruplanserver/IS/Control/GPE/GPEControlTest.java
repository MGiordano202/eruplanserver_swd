package it.unisa.eruplanserver.IS.Control.GPE;

import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaPericolo;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaSicura;
import it.unisa.eruplanserver.IS.Exception.GPE.GPEException;
import it.unisa.eruplanserver.IS.Service.GPE.GPEService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test di integrazione HTTP → GPEControl → GPEService (mockato): generazione del piano di evacuazione.
 * È il percorso critico dell'applicazione: un operatore crea il piano e parte l'allerta.
 */
@WebMvcTest(GPEControl.class)
@DisplayName("GPEControl - generazione del piano di evacuazione via HTTP")
class GPEControlTest {

    private static final String SUCCESSO = "{\"message\": \"Piano generato e notifiche inviate con successo.\"}";

    // JSON volutamente malformato, per verificare la risposta 400 (TEXT: IntelliJ non lo valida come JSON)
    // language=TEXT
    private static final String JSON_NON_VALIDO = "{nome:";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GPEService gpeService;

    private static MockHttpSession operatore() {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("isOperatore", true);
        return s;
    }

    @Test
    @DisplayName("Senza login da operatore: 401 e piano non generato")
    void senzaLogin() throws Exception {
        mvc.perform(post("/gestorePiani/genera").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Piano A\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Accesso negato: Operatore non loggato."));
        verifyNoInteractions(gpeService);
    }

    @Test
    @DisplayName("JSON non valido: 400 e piano non generato")
    void jsonNonValido() throws Exception {
        mvc.perform(post("/gestorePiani/genera").session(operatore()).contentType(MediaType.APPLICATION_JSON).content(JSON_NON_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Errore nel parsing del JSON."));
        verifyNoInteractions(gpeService);
    }

    @Test
    @DisplayName("Dati rifiutati dal service: 400 con il messaggio del service")
    void datiRifiutati() throws Exception {
        doThrow(new GPEException("ERRORE - ESISTE GIÀ UN PIANO CON QUESTO NOME."))
                .when(gpeService).generaPiano(eq("Piano A"), any(), any());

        mvc.perform(post("/gestorePiani/genera").session(operatore()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Piano A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("ERRORE - ESISTE GIÀ UN PIANO CON QUESTO NOME."));
    }

    @Test
    @DisplayName("Solo il nome: 200 e service chiamato senza zone")
    void soloNome() throws Exception {
        mvc.perform(post("/gestorePiani/genera").session(operatore()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Piano A\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(SUCCESSO));
        verify(gpeService).generaPiano("Piano A", null, null);
    }

    @Test
    @DisplayName("Piano completo con zona di pericolo e zona sicura: 200 e zone passate al service")
    void pianoCompleto() throws Exception {
        String body = """
                {"nome":"Piano Vesuvio",
                 "zonaPericolo":{"punti":[{"lat":40.82,"lon":14.42},{"lat":40.83,"lon":14.43},
                                          {"lat":40.81,"lon":14.44},{"lat":40.82,"lon":14.42}]},
                 "zoneSicure":{"coordinate":{"lat":40.85,"lon":14.26},"raggio":120}}""";

        mvc.perform(post("/gestorePiani/genera").session(operatore()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(SUCCESSO));

        ArgumentCaptor<ZonaPericolo> pericolo = ArgumentCaptor.forClass(ZonaPericolo.class);
        ArgumentCaptor<ZonaSicura> sicura = ArgumentCaptor.forClass(ZonaSicura.class);
        verify(gpeService).generaPiano(eq("Piano Vesuvio"), pericolo.capture(), sicura.capture());
        assertEquals(List.of(new Punto(40.82, 14.42), new Punto(40.83, 14.43), new Punto(40.81, 14.44), new Punto(40.82, 14.42)),
                pericolo.getValue().getPunti());
        assertEquals(new Punto(40.85, 14.26), sicura.getValue().getCoordinate());
        assertEquals(120, sicura.getValue().getRaggio());
    }

    @Test
    @DisplayName("Zone con una struttura sbagliata: 400 e piano non generato")
    void zoneMalformate() throws Exception {
        String body = "{\"nome\":\"Piano Vesuvio\",\"zonaPericolo\":\"non sono una zona\"}";

        mvc.perform(post("/gestorePiani/genera").session(operatore()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Formato delle zone non valido."));
        verifyNoInteractions(gpeService);
    }
}
