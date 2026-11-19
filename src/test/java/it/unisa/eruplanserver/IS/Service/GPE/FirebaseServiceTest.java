package it.unisa.eruplanserver.IS.Service.GPE;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test per {@link FirebaseService}. FirebaseMessaging viene simulato,
 * quindi nessuna notifica reale viene inviata e non servono credenziali.
 */
@DisplayName("FirebaseService - invio notifiche broadcast")
class FirebaseServiceTest {

    private final FirebaseService service = new FirebaseService();
    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private MockedStatic<FirebaseMessaging> firebaseStatic;

    @BeforeEach
    void setUp() {
        firebaseStatic = mockStatic(FirebaseMessaging.class);
        firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(messaging);
    }

    @AfterEach
    void tearDown() {
        // Rilascia il mock statico: ogni test resta isolato dagli altri
        firebaseStatic.close();
    }

    @Test
    @DisplayName("Invio riuscito: restituisce l'id del messaggio di Firebase e invia un solo messaggio")
    void invioRiuscito() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/eruplan/messages/42");

        String risposta = service.inviaNotificaBroadcast("Titolo", "Corpo", "emergenza");

        assertEquals("projects/eruplan/messages/42", risposta);
        verify(messaging, times(1)).send(any(Message.class));
    }

    @Test
    @DisplayName("Errore di Firebase: nessuna eccezione verso il chiamante, restituisce il messaggio d'errore")
    void invioFallito() throws Exception {
        when(messaging.send(any(Message.class))).thenThrow(new IllegalStateException("rete assente"));

        String risposta = assertDoesNotThrow(() -> service.inviaNotificaBroadcast("Titolo", "Corpo", "emergenza"));

        assertEquals("Errore nell'invio: rete assente", risposta);
    }

    @Test
    @DisplayName("Firebase non inizializzato: restituisce un errore invece di bloccare il piano")
    void firebaseNonInizializzato() {
        firebaseStatic.when(FirebaseMessaging::getInstance)
                .thenThrow(new IllegalStateException("FirebaseApp non inizializzata"));

        String risposta = service.inviaNotificaBroadcast("Titolo", "Corpo", "emergenza");

        assertEquals("Errore nell'invio: FirebaseApp non inizializzata", risposta);
    }

    @Test
    @DisplayName("Topic non valido: il messaggio non viene costruito e non parte nessun invio")
    void topicNonValido() throws Exception {
        String risposta = service.inviaNotificaBroadcast("Titolo", "Corpo", "topic con spazi!");

        assertTrue(risposta.startsWith("Errore nell'invio:"), risposta);
        verify(messaging, never()).send(any(Message.class));
    }
}
