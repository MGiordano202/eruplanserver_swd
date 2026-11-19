package it.unisa.eruplanserver.IS.Utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit test per le regole di validazione dei campi in {@link Validator}.
 * I controlli sulle zone (creaZoneSicure, creaZonaPericolo) sono già coperti da ZonaServiceTest.
 */
@DisplayName("Validator - regole di validazione dei campi")
class ValidatorTest {

    // Letterale (non "a".repeat(30)): le annotazioni accettano solo costanti
    private static final String TRENTA_LETTERE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Nested
    @DisplayName("Nome e cognome")
    class NomeCognome {

        @ParameterizedTest
        @ValueSource(strings = {"Al", "Mario", "Anna Maria", TRENTA_LETTERE})
        @DisplayName("Accetta 2-30 lettere e spazi")
        void validi(String valore) {
            assertTrue(Validator.isNomeValid(valore));
            assertTrue(Validator.isCognomeValid(valore));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"A", TRENTA_LETTERE + "a", "Mario1", "D'Amico", "Rossi-Bianchi"})
        @DisplayName("Rifiuta null, vuoto, lunghezza fuori range, cifre e simboli")
        void nonValidi(String valore) {
            assertFalse(Validator.isNomeValid(valore));
            assertFalse(Validator.isCognomeValid(valore));
        }
    }

    @Nested
    @DisplayName("Codice fiscale")
    class CodiceFiscale {

        @ParameterizedTest
        @ValueSource(strings = {"RSSMRA80A01H703X", "rssmra80a01h703x"})
        @DisplayName("Accetta 16 caratteri alfanumerici")
        void valido(String cf) {
            assertTrue(Validator.isCodiceFiscaleValid(cf));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"RSSMRA80A01H703", "RSSMRA80A01H703XY", "RSSMRA80A01H70-X", "RSSMRA80A01H703 "})
        @DisplayName("Rifiuta null, vuoto, 15/17 caratteri e simboli")
        void nonValido(String cf) {
            assertFalse(Validator.isCodiceFiscaleValid(cf));
        }

        @Test
        @DisplayName("Controllo lunghezza: solo 16 caratteri esatti")
        void lunghezza() {
            assertTrue(Validator.isCodiceFiscaleLengthValid("RSSMRA80A01H703X"));
            assertFalse(Validator.isCodiceFiscaleLengthValid("RSSMRA80A01H703"));
            assertFalse(Validator.isCodiceFiscaleLengthValid("RSSMRA80A01H703XY"));
            assertFalse(Validator.isCodiceFiscaleLengthValid(null));
        }

        @Test
        @DisplayName("Controllo caratteri (TD-M-20): lettere e cifre 1-9, lo zero è escluso")
        void caratteri() {
            assertTrue(Validator.isCodiceFiscaleCharactersValid("RSSMRA81A11H713X"));
            assertFalse(Validator.isCodiceFiscaleCharactersValid("RSSMRA80A01H703X"));
            assertFalse(Validator.isCodiceFiscaleCharactersValid("RSSMRA81A11H71-X"));
            assertFalse(Validator.isCodiceFiscaleCharactersValid(null));
        }
    }

    @Nested
    @DisplayName("Sesso")
    class Sesso {

        @ParameterizedTest
        @ValueSource(strings = {"M", "F"})
        @DisplayName("Accetta solo M e F")
        void valido(String sesso) {
            assertTrue(Validator.isSessoValid(sesso));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"m", "f", "X", "MF", " M"})
        @DisplayName("Rifiuta minuscole, altri valori, null e vuoto")
        void nonValido(String sesso) {
            assertFalse(Validator.isSessoValid(sesso));
        }
    }

    @Nested
    @DisplayName("Password")
    class Password {

        @ParameterizedTest
        @ValueSource(strings = {"Password1", "Abcdefg1", "UnaPasswordMoltoLunga2024!"})
        @DisplayName("Accetta almeno 8 caratteri con minuscola, maiuscola e cifra")
        void valida(String password) {
            assertTrue(Validator.isPasswordValid(password));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"Abcdef1", "password1", "PASSWORD1", "Password"})
        @DisplayName("Rifiuta password corte o senza maiuscola, minuscola o cifra")
        void nonValida(String password) {
            assertFalse(Validator.isPasswordValid(password));
        }
    }

    @Nested
    @DisplayName("Data di nascita")
    class DataNascita {

        @Test
        @DisplayName("Valida solo se nel passato")
        void data() {
            LocalDate oggi = LocalDate.now();
            assertTrue(Validator.isDataNascitaValid(oggi.minusDays(1)));
            assertTrue(Validator.isDataNascitaValid(LocalDate.of(1950, 6, 15)));
            assertFalse(Validator.isDataNascitaValid(oggi.plusDays(1)));
            assertFalse(Validator.isDataNascitaValid(null));
        }

        @Test
        @DisplayName("Oggi non è una data di nascita valida")
        void oggi() {
            LocalDate oggi = LocalDate.now();
            boolean valida = Validator.isDataNascitaValid(oggi);
            // Repeatable: se la mezzanotte cade tra le due letture dell'orologio il risultato
            // non è significativo, quindi il test viene saltato invece di fallire a caso
            assumeTrue(oggi.equals(LocalDate.now()), "cambio di giorno durante il test");
            assertFalse(valida);
        }

        @ParameterizedTest
        @ValueSource(strings = {"29/02/2024"})
        @DisplayName("29 febbraio accettato negli anni bisestili")
        void bisestile(String data) {
            assertTrue(Validator.isDataNascitaFormatoValid(data));
        }

        @ParameterizedTest
        @ValueSource(strings = {"31/02/2020", "30/02/2024", "29/02/2023", "31/04/2020"})
        @DisplayName("Giorni che non esistono nel mese rifiutati")
        void giornoInesistente(String data) {
            assertFalse(Validator.isDataNascitaFormatoValid(data));
        }

        @ParameterizedTest
        @ValueSource(strings = {"15/03/1990", "01/01/2000", "31/12/1999"})
        @DisplayName("Formato dd/MM/yyyy accettato")
        void formatoValido(String data) {
            assertTrue(Validator.isDataNascitaFormatoValid(data));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "1990-03-15", "15-03-1990", "32/01/2020", "15/13/2020", "1/3/1990", "abc"})
        @DisplayName("Formati diversi o valori fuori range rifiutati")
        void formatoNonValido(String data) {
            assertFalse(Validator.isDataNascitaFormatoValid(data));
        }
    }

    @Nested
    @DisplayName("Nome del piano di evacuazione")
    class NomePiano {

        @ParameterizedTest
        @ValueSource(strings = {"abc", "Piano Vesuvio 1", "  Piano A  ", "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r"})
        @DisplayName("Accetta 3-35 caratteri alfanumerici e spazi (spazi esterni ignorati)")
        void valido(String nome) {
            assertTrue(Validator.isNomePianoValid(nome));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "ab", "  ab  ", "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8", "Piano-1", "Piano@Vesuvio"})
        @DisplayName("Rifiuta vuoto, meno di 3 o più di 35 caratteri e simboli")
        void nonValido(String nome) {
            assertFalse(Validator.isNomePianoValid(nome));
        }
    }

    @Nested
    @DisplayName("Indirizzo")
    class Indirizzo {

        private static final String[] VALIDO = {"Via Roma", "12/B", "Napoli", "80100", "Napoli", "Campania", "Italia"};

        @Test
        @DisplayName("Accetta un indirizzo completo e valido")
        void valido() {
            assertTrue(valida(VALIDO));
        }

        static Stream<Arguments> limitiValidi() {
            return Stream.of(
                    Arguments.of(0, "a"), Arguments.of(0, "a".repeat(40)),
                    Arguments.of(1, "1"), Arguments.of(1, "123456"),
                    Arguments.of(2, "Ab"), Arguments.of(2, "a".repeat(40)),
                    Arguments.of(4, "Bari"), Arguments.of(4, "a".repeat(20)),
                    Arguments.of(5, "Lazio"), Arguments.of(5, "a".repeat(25)),
                    Arguments.of(6, "Peru"), Arguments.of(6, "a".repeat(40))
            );
        }

        @ParameterizedTest(name = "campo {0} = \"{1}\"")
        @MethodSource("limitiValidi")
        @DisplayName("Accetta i valori ai limiti minimo e massimo di ogni campo")
        void limiteValido(int indice, String valore) {
            String[] campi = VALIDO.clone();
            campi[indice] = valore;
            assertTrue(valida(campi));
        }

        static Stream<Arguments> campiNonValidi() {
            return Stream.of(
                    Arguments.of(0, null), Arguments.of(0, "Via Roma!"), Arguments.of(0, "a".repeat(41)),
                    Arguments.of(1, null), Arguments.of(1, ""), Arguments.of(1, "1234567"),
                    Arguments.of(2, null), Arguments.of(2, "N"), Arguments.of(2, "Napoli1"),
                    Arguments.of(3, null), Arguments.of(3, "8010"), Arguments.of(3, "801000"), Arguments.of(3, "8010A"),
                    Arguments.of(2, "a".repeat(41)),
                    Arguments.of(4, null), Arguments.of(4, "NA"), Arguments.of(4, "a".repeat(21)),
                    Arguments.of(5, null), Arguments.of(5, "Lazi"), Arguments.of(5, "a".repeat(26)),
                    Arguments.of(6, null), Arguments.of(6, "UK"), Arguments.of(6, "a".repeat(41))
            );
        }

        @ParameterizedTest(name = "campo {0} = \"{1}\"")
        @MethodSource("campiNonValidi")
        @DisplayName("Rifiuta l'indirizzo se anche un solo campo non è valido")
        void campoNonValido(int indice, String valore) {
            String[] campi = VALIDO.clone();
            campi[indice] = valore;
            assertFalse(valida(campi));
        }

        private boolean valida(String[] c) {
            return Validator.isIndirizzoValid(c[0], c[1], c[2], c[3], c[4], c[5], c[6]);
        }
    }

    @Nested
    @DisplayName("Campi booleani obbligatori e helper di lunghezza")
    class Altri {

        @Test
        @DisplayName("Assistenza e minorenne: definiti solo se non null")
        void booleani() {
            assertTrue(Validator.isAssistenzaDefinita(true));
            assertTrue(Validator.isAssistenzaDefinita(false));
            assertFalse(Validator.isAssistenzaDefinita(null));
            assertTrue(Validator.isMinorenneDefinito(true));
            assertTrue(Validator.isMinorenneDefinito(false));
            assertFalse(Validator.isMinorenneDefinito(null));
        }

        @Test
        @DisplayName("isTroppoCorto / isTroppoLungo rispettano i limiti inclusi")
        void lunghezze() {
            assertTrue(Validator.isTroppoCorto("ab", 3));
            assertFalse(Validator.isTroppoCorto("abc", 3));
            assertTrue(Validator.isTroppoCorto(null, 3));
            assertTrue(Validator.isTroppoLungo("abcd", 3));
            assertFalse(Validator.isTroppoLungo("abc", 3));
            assertFalse(Validator.isTroppoLungo(null, 3));
        }
    }
}
