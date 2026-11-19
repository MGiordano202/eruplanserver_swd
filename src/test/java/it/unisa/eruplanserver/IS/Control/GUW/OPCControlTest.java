package it.unisa.eruplanserver.IS.Control.GUW;

import it.unisa.eruplanserver.IS.Exception.GUW.LoginPasswordsMismatchException;
import it.unisa.eruplanserver.IS.Exception.GUW.OPCNotFoundException;
import it.unisa.eruplanserver.IS.Service.GUW.OPCService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test di integrazione HTTP → OPCControl → OPCService (mockato): login e logout degli operatori.
 */
@WebMvcTest(OPCControl.class)
@DisplayName("OPCControl - login e logout degli operatori via HTTP")
class OPCControlTest {

    private static final String LOGIN = "{\"codiceFiscale\":\"VRDLGU75B02F839K\",\"password\":\"Operatore1\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private OPCService opcService;

    @Test
    @DisplayName("Credenziali corrette: 200 e sessione da operatore con il CF")
    void successo() throws Exception {
        mvc.perform(post("/gestoreUtentiWeb/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute("isOperatore", true))
                .andExpect(request().sessionAttribute("codiceFiscale", "VRDLGU75B02F839K"));
        verify(opcService).login("VRDLGU75B02F839K", "Operatore1");
    }

    @Test
    @DisplayName("Operatore inesistente: 404 e nessuna sessione da operatore")
    void operatoreInesistente() throws Exception {
        doThrow(new OPCNotFoundException("ERRORE - OPERATORE NON TROVATO.")).when(opcService).login(any(), any());

        mvc.perform(post("/gestoreUtentiWeb/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("ERRORE - OPERATORE NON TROVATO."))
                .andExpect(request().sessionAttribute("isOperatore", nullValue()));
    }

    @Test
    @DisplayName("Password errata: 401 e nessuna sessione da operatore")
    void passwordErrata() throws Exception {
        doThrow(new LoginPasswordsMismatchException("ERRORE - PASSWORD ERRATA.")).when(opcService).login(any(), any());

        mvc.perform(post("/gestoreUtentiWeb/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("ERRORE - PASSWORD ERRATA."))
                .andExpect(request().sessionAttribute("isOperatore", nullValue()));
    }

    @Test
    @DisplayName("JSON non valido: errore tecnico e service non chiamato")
    void jsonNonValido() throws Exception {
        mvc.perform(post("/gestoreUtentiWeb/login").contentType(MediaType.APPLICATION_JSON).content("{cf:"))
                .andExpect(status().isInternalServerError())
                .andExpect(status().reason("ERRORE TECNICO"));
        verifyNoInteractions(opcService);
    }

    @Test
    @DisplayName("Logout: 200 e sessione invalidata")
    void logout() throws Exception {
        MockHttpSession sessione = new MockHttpSession();
        sessione.setAttribute("isOperatore", true);

        mvc.perform(post("/gestoreUtentiWeb/logout").session(sessione))
                .andExpect(status().isOk());

        assertTrue(sessione.isInvalid());
    }

    @Test
    @DisplayName("Logout senza sessione: 200 senza errori")
    void logoutSenzaSessione() throws Exception {
        mvc.perform(post("/gestoreUtentiWeb/logout"))
                .andExpect(status().isOk());
    }
}
