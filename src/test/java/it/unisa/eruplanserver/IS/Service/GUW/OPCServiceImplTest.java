package it.unisa.eruplanserver.IS.Service.GUW;

import it.unisa.eruplanserver.IS.Entity.GUW.OPCEntity;
import it.unisa.eruplanserver.IS.Exception.GUW.LoginPasswordsMismatchException;
import it.unisa.eruplanserver.IS.Exception.GUW.OPCNotFoundException;
import it.unisa.eruplanserver.IS.Repository.GUW.OPCRepository;
import it.unisa.eruplanserver.IS.Utility.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test per {@link OPCServiceImpl}: login degli operatori della protezione civile.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OPCServiceImpl - login operatori")
class OPCServiceImplTest {

    private static final String CF = "VRDLGU75B02F839K";
    private static final String PASSWORD = "Operatore1";

    @Mock
    private OPCRepository opcRepository;

    @InjectMocks
    private OPCServiceImpl service;

    private OPCEntity operatore;

    @BeforeEach
    void setUp() throws Exception {
        operatore = OPCEntity.builder()
                .codiceFiscale(CF)
                .password(Utility.encrypt(PASSWORD))
                .nome("Luigi")
                .cognome("Verdi")
                .build();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CORTO", "VRDLGU75B02F839KX", "VRDLGU75B02F83-K"})
    @DisplayName("CF con formato non valido: errore senza interrogare il database")
    void cfNonValido(String cf) {
        OPCNotFoundException e = assertThrows(OPCNotFoundException.class, () -> service.login(cf, PASSWORD));

        assertEquals("ERRORE - FORMATO CODICE FISCALE NON VALIDO.", e.getMessage());
        verifyNoInteractions(opcRepository);
    }

    @Test
    @DisplayName("Operatore inesistente: OPCNotFoundException")
    void operatoreNonTrovato() {
        when(opcRepository.findByCodiceFiscale(CF)).thenReturn(null);

        OPCNotFoundException e = assertThrows(OPCNotFoundException.class, () -> service.login(CF, PASSWORD));
        assertEquals("ERRORE - OPERATORE NON TROVATO.", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Operatore2", "operatore1", ""})
    @DisplayName("Password errata: LoginPasswordsMismatchException")
    void passwordErrata(String password) {
        when(opcRepository.findByCodiceFiscale(CF)).thenReturn(operatore);

        LoginPasswordsMismatchException e = assertThrows(LoginPasswordsMismatchException.class,
                () -> service.login(CF, password));
        assertEquals("ERRORE - PASSWORD ERRATA.", e.getMessage());
    }

    @Test
    @DisplayName("Password mancante nella richiesta (null): errore di password, non un crash")
    void passwordNull() {
        when(opcRepository.findByCodiceFiscale(CF)).thenReturn(operatore);

        assertThrows(LoginPasswordsMismatchException.class, () -> service.login(CF, null));
    }

    @Test
    @DisplayName("Credenziali corrette: nessuna eccezione")
    void successo() {
        when(opcRepository.findByCodiceFiscale(CF)).thenReturn(operatore);

        assertDoesNotThrow(() -> service.login(CF, PASSWORD));
        verify(opcRepository).findByCodiceFiscale(CF);
    }
}
