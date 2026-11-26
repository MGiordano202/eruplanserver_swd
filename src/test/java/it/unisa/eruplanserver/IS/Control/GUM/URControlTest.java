package it.unisa.eruplanserver.IS.Control.GUM;

import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Exception.GUM.InvalidURDataException;
import it.unisa.eruplanserver.IS.Exception.GUM.LoginPasswordsMismatchException;
import it.unisa.eruplanserver.IS.Exception.GUM.URNotFoundException;
import it.unisa.eruplanserver.IS.Service.GUM.URService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test di integrazione HTTP → URControl → URService: viene avviato solo lo strato web di Spring
 * (routing, lettura del body, sessione, codici HTTP); URService è mockato.
 */
@WebMvcTest(URControl.class)
@DisplayName("URControl - registrazione, login e logout dei cittadini via HTTP")
class URControlTest {

    private static final String REGISTRAZIONE_VALIDA = """
            {"nome":" Mario ","cognome":"Rossi","codiceFiscale":"rssmra80a01h703x",
             "password":"Password1","sesso":"m","dataNascita":"15-03-1990"}""";

    // JSON volutamente malformato, per verificare la risposta 400 (TEXT: IntelliJ non lo valida come JSON)
    // language=TEXT
    private static final String JSON_NON_VALIDO = "{nome:";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private URService urService;

    @Nested
    @DisplayName("POST /gestoreUtentiMobile/registra")
    class Registra {

        @Test
        @DisplayName("Dati validi: 201 e utente passato al service ripulito (trim, maiuscole, data)")
        void successo() throws Exception {
            mvc.perform(post("/gestoreUtentiMobile/registra").contentType(MediaType.APPLICATION_JSON).content(REGISTRAZIONE_VALIDA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Registrazione completata con successo"));

            ArgumentCaptor<UREntity> captor = ArgumentCaptor.forClass(UREntity.class);
            verify(urService).registra(captor.capture());
            UREntity utente = captor.getValue();
            assertAll(
                    () -> assertEquals("Mario", utente.getNome()),
                    () -> assertEquals("RSSMRA80A01H703X", utente.getCodiceFiscale()),
                    () -> assertEquals("M", utente.getSesso()),
                    () -> assertEquals(LocalDate.of(1990, 3, 15), utente.getDataDiNascita()),
                    () -> assertEquals("Password1", utente.getPassword(), "l'hash è compito del service"));
        }

        @ParameterizedTest(name = "manca {0}")
        @ValueSource(strings = {"nome", "cognome", "codiceFiscale", "password", "sesso", "dataNascita"})
        @DisplayName("Campo obbligatorio mancante: 400 e service non chiamato")
        void campoMancante(String campo) throws Exception {
            String body = REGISTRAZIONE_VALIDA.replaceAll("\"" + campo + "\":\"[^\"]*\",?", "").replace(",}", "}");

            mvc.perform(post("/gestoreUtentiMobile/registra").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(status().reason("Errore: tutti i campi sono obbligatori!"));
            verifyNoInteractions(urService);
        }

        @Test
        @DisplayName("JSON non valido: 400")
        void jsonNonValido() throws Exception {
            mvc.perform(post("/gestoreUtentiMobile/registra").contentType(MediaType.APPLICATION_JSON).content(JSON_NON_VALIDO))
                    .andExpect(status().isBadRequest())
                    .andExpect(status().reason("Errore: formato JSON non valido"));
            verifyNoInteractions(urService);
        }

        @Test
        @DisplayName("Data in formato diverso da GG-MM-AAAA: 400")
        void dataFormatoErrato() throws Exception {
            mvc.perform(post("/gestoreUtentiMobile/registra").contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTRAZIONE_VALIDA.replace("15-03-1990", "1990-03-15")))
                    .andExpect(status().isBadRequest())
                    .andExpect(status().reason("Formato data errato. Usa: GG-MM-AAAA"));
            verifyNoInteractions(urService);
        }

        @Test
        @DisplayName("Errore imprevisto del service (es. database non raggiungibile): 500 con il motivo")
        void erroreImprevisto() throws Exception {
            doThrow(new IllegalStateException("database non raggiungibile")).when(urService).registra(any());

            mvc.perform(post("/gestoreUtentiMobile/registra").contentType(MediaType.APPLICATION_JSON).content(REGISTRAZIONE_VALIDA))
                    .andExpect(status().isInternalServerError())
                    .andExpect(status().reason("Errore interno: database non raggiungibile"));
        }

        @Test
        @DisplayName("Algoritmo di hash non disponibile: 500 errore tecnico")
        void erroreTecnico() throws Exception {
            doThrow(new java.security.NoSuchAlgorithmException("SHA-256")).when(urService).registra(any());

            mvc.perform(post("/gestoreUtentiMobile/registra").contentType(MediaType.APPLICATION_JSON).content(REGISTRAZIONE_VALIDA))
                    .andExpect(status().isInternalServerError())
                    .andExpect(status().reason("Errore tecnico del server"));
        }

        @Test
        @DisplayName("Dati rifiutati dal service: 400 con il messaggio del service")
        void datiRifiutati() throws Exception {
            doThrow(new InvalidURDataException("errore: codice fiscale già registrato")).when(urService).registra(any());

            mvc.perform(post("/gestoreUtentiMobile/registra").contentType(MediaType.APPLICATION_JSON).content(REGISTRAZIONE_VALIDA))
                    .andExpect(status().isBadRequest())
                    .andExpect(status().reason("errore: codice fiscale già registrato"));
        }
    }

    @Nested
    @DisplayName("POST /gestoreUtentiMobile/login e GET /logout")
    class LoginLogout {

        private static final String LOGIN = "{\"codiceFiscale\":\"RSSMRA80A01H703X\",\"password\":\"Password1\"}";

        @Test
        @DisplayName("Credenziali corrette: 200 e sessione da cittadino con il CF")
        void successo() throws Exception {
            mvc.perform(post("/gestoreUtentiMobile/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                    .andExpect(status().isOk())
                    .andExpect(request().sessionAttribute("isCittadino", true))
                    .andExpect(request().sessionAttribute("codiceFiscale", "RSSMRA80A01H703X"));
            verify(urService).login("RSSMRA80A01H703X", "Password1");
        }

        @Test
        @DisplayName("Utente inesistente: 404 e nessuna sessione di login")
        void utenteInesistente() throws Exception {
            doThrow(new URNotFoundException("ERRORE - UTENTE NON TROVATO.")).when(urService).login(any(), any());

            mvc.perform(post("/gestoreUtentiMobile/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                    .andExpect(status().isNotFound())
                    .andExpect(status().reason("ERRORE - UTENTE NON TROVATO."))
                    .andExpect(request().sessionAttribute("isCittadino", nullValue()));
        }

        @Test
        @DisplayName("Password errata: 401 e nessuna sessione di login")
        void passwordErrata() throws Exception {
            doThrow(new LoginPasswordsMismatchException("ERRORE - PASSWORD ERRATA.")).when(urService).login(any(), any());

            mvc.perform(post("/gestoreUtentiMobile/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(status().reason("ERRORE - PASSWORD ERRATA."))
                    .andExpect(request().sessionAttribute("isCittadino", nullValue()));
        }

        @Test
        @DisplayName("Logout: 200 e sessione invalidata")
        void logout() throws Exception {
            MockHttpSession sessione = new MockHttpSession();
            sessione.setAttribute("isCittadino", true);

            mvc.perform(get("/gestoreUtentiMobile/logout").session(sessione))
                    .andExpect(status().isOk());

            assertTrue(sessione.isInvalid());
        }

        private static org.hamcrest.Matcher<Object> nullValue() {
            return org.hamcrest.Matchers.nullValue();
        }
    }
}
