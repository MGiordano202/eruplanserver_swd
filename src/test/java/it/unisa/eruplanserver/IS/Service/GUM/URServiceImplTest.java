package it.unisa.eruplanserver.IS.Service.GUM;

import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import it.unisa.eruplanserver.IS.Exception.GUM.InvalidURDataException;
import it.unisa.eruplanserver.IS.Exception.GUM.LoginPasswordsMismatchException;
import it.unisa.eruplanserver.IS.Exception.GUM.URNotFoundException;
import it.unisa.eruplanserver.IS.Repository.GUM.URRepository;
import it.unisa.eruplanserver.IS.Utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test per {@link URServiceImpl}: login e registrazione degli utenti registrati (cittadini).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("URServiceImpl - login e registrazione cittadini")
class URServiceImplTest {

    private static final String CF = "RSSMRA80A01H703X";
    private static final String PASSWORD = "Password1";

    @Mock
    private URRepository urRepository;

    @InjectMocks
    private URServiceImpl service;

    private static UREntity utenteValido() {
        return UREntity.builder()
                .nome("Mario")
                .cognome("Rossi")
                .codiceFiscale(CF)
                .password(PASSWORD)
                .sesso("M")
                .dataDiNascita(LocalDate.of(1980, 1, 1))
                .build();
    }

    @Nested
    @DisplayName("login")
    class Login {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"CORTO", "RSSMRA80A01H70-X"})
        @DisplayName("CF con formato non valido: errore senza interrogare il database")
        void cfNonValido(String cf) {
            URNotFoundException e = assertThrows(URNotFoundException.class, () -> service.login(cf, PASSWORD));

            assertEquals("ERRORE - FORMATO CODICE FISCALE NON VALIDO.", e.getMessage());
            verifyNoInteractions(urRepository);
        }

        @Test
        @DisplayName("Utente inesistente: URNotFoundException")
        void utenteNonTrovato() {
            when(urRepository.findByCodiceFiscale(CF)).thenReturn(null);

            URNotFoundException e = assertThrows(URNotFoundException.class, () -> service.login(CF, PASSWORD));
            assertEquals("ERRORE - UTENTE NON TROVATO.", e.getMessage());
        }

        @Test
        @DisplayName("Password errata: LoginPasswordsMismatchException")
        void passwordErrata() throws Exception {
            UREntity salvato = utenteValido();
            salvato.setPassword(Utility.encrypt(PASSWORD));
            when(urRepository.findByCodiceFiscale(CF)).thenReturn(salvato);

            LoginPasswordsMismatchException e = assertThrows(LoginPasswordsMismatchException.class,
                    () -> service.login(CF, "PasswordSbagliata1"));
            assertEquals("ERRORE - PASSWORD ERRATA.", e.getMessage());
        }

        @Test
        @DisplayName("Il confronto avviene sull'hash: la password in chiaro salvata non basta")
        void passwordInChiaroNelDbRifiutata() {
            UREntity salvato = utenteValido(); // password in chiaro, non hashata
            when(urRepository.findByCodiceFiscale(CF)).thenReturn(salvato);

            assertThrows(LoginPasswordsMismatchException.class, () -> service.login(CF, PASSWORD));
        }

        @Test
        @DisplayName("Password mancante nella richiesta (null): errore di password, non un crash")
        void passwordNull() throws Exception {
            UREntity salvato = utenteValido();
            salvato.setPassword(Utility.encrypt(PASSWORD));
            when(urRepository.findByCodiceFiscale(CF)).thenReturn(salvato);

            assertThrows(LoginPasswordsMismatchException.class, () -> service.login(CF, null));
        }

        @Test
        @DisplayName("Credenziali corrette: nessuna eccezione")
        void successo() throws Exception {
            UREntity salvato = utenteValido();
            salvato.setPassword(Utility.encrypt(PASSWORD));
            when(urRepository.findByCodiceFiscale(CF)).thenReturn(salvato);

            assertDoesNotThrow(() -> service.login(CF, PASSWORD));
            verify(urRepository).findByCodiceFiscale(CF);
        }
    }

    @Nested
    @DisplayName("registra")
    class Registra {

        static Stream<Arguments> datiNonValidi() {
            return Stream.of(
                    Arguments.of("password senza maiuscola", (Consumer<UREntity>) u -> u.setPassword("password1"), "errore: password non valida"),
                    Arguments.of("password corta", (Consumer<UREntity>) u -> u.setPassword("Pass1"), "errore: password non valida"),
                    Arguments.of("nome con cifre", (Consumer<UREntity>) u -> u.setNome("Mario1"), "errore: inserisci un nome conforme"),
                    Arguments.of("nome di una lettera", (Consumer<UREntity>) u -> u.setNome("M"), "errore: inserisci un nome conforme"),
                    Arguments.of("sesso non valido", (Consumer<UREntity>) u -> u.setSesso("X"), "errore: sesso non identificato"),
                    Arguments.of("data nel futuro", (Consumer<UREntity>) u -> u.setDataDiNascita(LocalDate.now().plusDays(1)), "errore: inserisci una data valida"),
                    Arguments.of("data mancante", (Consumer<UREntity>) u -> u.setDataDiNascita(null), "errore: inserisci una data valida"),
                    Arguments.of("cognome con simboli", (Consumer<UREntity>) u -> u.setCognome("Rossi!"), "errore: inserisci un cognome conforme!"),
                    // Campi assenti nel JSON della richiesta: arrivano null dal controller
                    Arguments.of("password mancante", (Consumer<UREntity>) u -> u.setPassword(null), "errore: password non valida"),
                    Arguments.of("nome mancante", (Consumer<UREntity>) u -> u.setNome(null), "errore: inserisci un nome conforme"),
                    Arguments.of("sesso mancante", (Consumer<UREntity>) u -> u.setSesso(null), "errore: sesso non identificato"),
                    Arguments.of("cognome mancante", (Consumer<UREntity>) u -> u.setCognome(null), "errore: inserisci un cognome conforme!"),
                    // Più campi errati: viene segnalato il primo controllato (password prima del nome)
                    Arguments.of("più campi errati", (Consumer<UREntity>) u -> { u.setPassword("x"); u.setNome("1"); }, "errore: password non valida")
            );
        }

        @Test
        @DisplayName("CF mancante nella richiesta (null): errore di formato e nessun salvataggio")
        void cfNull() {
            UREntity utente = utenteValido();
            utente.setCodiceFiscale(null);

            assertThrows(URNotFoundException.class, () -> service.registra(utente));
            verifyNoInteractions(urRepository);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("datiNonValidi")
        @DisplayName("Dati non validi: InvalidURDataException e nessun salvataggio")
        void datoNonValido(String caso, Consumer<UREntity> modifica, String messaggio) {
            UREntity utente = utenteValido();
            modifica.accept(utente);

            InvalidURDataException e = assertThrows(InvalidURDataException.class, () -> service.registra(utente));

            assertEquals(messaggio, e.getMessage());
            verify(urRepository, never()).save(any());
        }

        @Test
        @DisplayName("CF non valido: URNotFoundException e nessun salvataggio")
        void cfNonValido() {
            UREntity utente = utenteValido();
            utente.setCodiceFiscale("CORTO");

            URNotFoundException e = assertThrows(URNotFoundException.class, () -> service.registra(utente));

            assertEquals("ERRORE - FORMATO CODICE FISCALE NON VALIDO.", e.getMessage());
            verifyNoInteractions(urRepository);
        }

        @Test
        @DisplayName("CF già registrato: InvalidURDataException e nessun salvataggio")
        void cfDuplicato() {
            when(urRepository.existsByCodiceFiscale(CF)).thenReturn(true);

            InvalidURDataException e = assertThrows(InvalidURDataException.class, () -> service.registra(utenteValido()));

            assertEquals("errore: codice fiscale già registrato", e.getMessage());
            verify(urRepository, never()).save(any());
        }

        @Test
        @DisplayName("Dati validi: salva l'utente con la password hashata, mai in chiaro")
        void successo() throws Exception {
            when(urRepository.existsByCodiceFiscale(CF)).thenReturn(false);
            UREntity utente = utenteValido();

            service.registra(utente);

            ArgumentCaptor<UREntity> salvato = ArgumentCaptor.forClass(UREntity.class);
            verify(urRepository).save(salvato.capture());
            assertAll(
                    () -> assertEquals(Utility.encrypt(PASSWORD), salvato.getValue().getPassword()),
                    () -> assertNotEquals(PASSWORD, salvato.getValue().getPassword()),
                    () -> assertEquals(CF, salvato.getValue().getCodiceFiscale()),
                    () -> assertEquals("Mario", salvato.getValue().getNome()),
                    () -> assertEquals("Rossi", salvato.getValue().getCognome()));
        }

        @Test
        @DisplayName("Un utente registrato può poi fare login con la sua password")
        void registraPoiLogin() throws Exception {
            when(urRepository.existsByCodiceFiscale(CF)).thenReturn(false);
            UREntity utente = utenteValido();
            service.registra(utente);
            when(urRepository.findByCodiceFiscale(anyString())).thenReturn(utente);

            assertDoesNotThrow(() -> service.login(CF, PASSWORD));
        }
    }
}
