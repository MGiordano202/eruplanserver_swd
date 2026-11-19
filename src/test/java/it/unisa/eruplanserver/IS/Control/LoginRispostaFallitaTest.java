package it.unisa.eruplanserver.IS.Control;

import it.unisa.eruplanserver.IS.Control.GUM.URControl;
import it.unisa.eruplanserver.IS.Control.GUW.OPCControl;
import it.unisa.eruplanserver.IS.Exception.GUM.GUMException;
import it.unisa.eruplanserver.IS.Exception.GUM.LoginPasswordsMismatchException;
import it.unisa.eruplanserver.IS.Exception.GUM.URNotFoundException;
import it.unisa.eruplanserver.IS.Exception.GUW.GUWException;
import it.unisa.eruplanserver.IS.Exception.GUW.OPCNotFoundException;
import it.unisa.eruplanserver.IS.Service.GUM.URService;
import it.unisa.eruplanserver.IS.Service.GUW.OPCService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test dei controller di login quando l'invio della risposta di errore fallisce
 * (es. il client si è disconnesso): sendError lancia IOException e il controller deve
 * trasformarla nell'eccezione del proprio modulo (GUMException / GUWException).
 * È l'unico punto in cui queste eccezioni vengono lanciate. Service, request e response sono mockati.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Login cittadini e operatori - errore nell'invio della risposta HTTP")
class LoginRispostaFallitaTest {

    private static final String LOGIN = "{\"codiceFiscale\":\"RSSMRA80A01H703X\",\"password\":\"Password1\"}";

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    @Nested
    @DisplayName("URControl (GUM)")
    class Cittadino {

        @Mock private URService urService;
        @InjectMocks private URControl controller;

        private void rispostaFallisce(int stato) throws IOException {
            doThrow(new IOException("connessione chiusa")).when(response).sendError(eq(stato), anyString());
        }

        @Test
        @DisplayName("Utente non trovato + invio fallito: GUMException")
        void utenteNonTrovato() throws Exception {
            doThrow(new URNotFoundException("ERRORE - UTENTE NON TROVATO.")).when(urService).login(any(), any());
            rispostaFallisce(HttpServletResponse.SC_NOT_FOUND);

            GUMException e = assertThrows(GUMException.class, () -> controller.login(LOGIN, request, response));
            assertEquals("ERRORE - UTENTE NON TROVATO.", e.getMessage());
            verifyNoInteractions(request);
        }

        @Test
        @DisplayName("Password errata + invio fallito: GUMException")
        void passwordErrata() throws Exception {
            doThrow(new LoginPasswordsMismatchException("ERRORE - PASSWORD ERRATA.")).when(urService).login(any(), any());
            rispostaFallisce(HttpServletResponse.SC_UNAUTHORIZED);

            GUMException e = assertThrows(GUMException.class, () -> controller.login(LOGIN, request, response));
            assertEquals("ERRORE - PASSWORD ERRATA.", e.getMessage());
        }

        @Test
        @DisplayName("Errore tecnico (algoritmo di hash assente) + invio fallito: GUMException")
        void erroreTecnico() throws Exception {
            doThrow(new NoSuchAlgorithmException("SHA-256")).when(urService).login(any(), any());
            rispostaFallisce(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            GUMException e = assertThrows(GUMException.class, () -> controller.login(LOGIN, request, response));
            assertEquals("ERRORE INTERNAL SERVER.", e.getMessage());
        }

        @Test
        @DisplayName("Errore tecnico con invio riuscito: risposta 500 \"ERRORE TECNICO\"")
        void erroreTecnicoRisposta() throws Exception {
            doThrow(new NoSuchAlgorithmException("SHA-256")).when(urService).login(any(), any());

            controller.login(LOGIN, request, response);

            verify(response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "ERRORE TECNICO");
        }
    }

    @Nested
    @DisplayName("OPCControl (GUW)")
    class Operatore {

        @Mock private OPCService opcService;
        @InjectMocks private OPCControl controller;

        private void rispostaFallisce(int stato) throws IOException {
            doThrow(new IOException("connessione chiusa")).when(response).sendError(eq(stato), anyString());
        }

        @Test
        @DisplayName("Operatore non trovato + invio fallito: GUWException")
        void operatoreNonTrovato() throws Exception {
            doThrow(new OPCNotFoundException("ERRORE - OPERATORE NON TROVATO.")).when(opcService).login(any(), any());
            rispostaFallisce(HttpServletResponse.SC_NOT_FOUND);

            GUWException e = assertThrows(GUWException.class, () -> controller.login(LOGIN, request, response));
            assertEquals("ERRORE - OPERATORE NON TROVATO.", e.getMessage());
            verifyNoInteractions(request);
        }

        @Test
        @DisplayName("Password errata + invio fallito: GUWException")
        void passwordErrata() throws Exception {
            doThrow(new it.unisa.eruplanserver.IS.Exception.GUW.LoginPasswordsMismatchException("ERRORE - PASSWORD ERRATA."))
                    .when(opcService).login(any(), any());
            rispostaFallisce(HttpServletResponse.SC_UNAUTHORIZED);

            GUWException e = assertThrows(GUWException.class, () -> controller.login(LOGIN, request, response));
            assertEquals("ERRORE - PASSWORD ERRATA.", e.getMessage());
        }

        @Test
        @DisplayName("JSON non valido + invio fallito: GUWException")
        void jsonNonValido() throws Exception {
            rispostaFallisce(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            GUWException e = assertThrows(GUWException.class, () -> controller.login("non json", request, response));
            assertEquals("ERRORE INTERNAL SERVER.", e.getMessage());
            verifyNoInteractions(opcService);
        }
    }
}
