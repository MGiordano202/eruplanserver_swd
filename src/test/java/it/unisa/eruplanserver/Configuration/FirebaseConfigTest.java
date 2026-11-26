package it.unisa.eruplanserver.Configuration;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test per {@link FirebaseConfig}: inizializzazione di Firebase all'avvio dell'applicazione.
 * Le credenziali sono un service account finto con una chiave RSA generata al momento:
 * nessuna credenziale reale e nessuna chiamata di rete (initializeApp non contatta Google).
 */
@DisplayName("FirebaseConfig - inizializzazione di Firebase all'avvio")
class FirebaseConfigTest {

    /** Service account nel formato del file JSON scaricato dalla console Firebase (private_key con \n letterali). */
    private static String credenzialiFinte;

    private final FirebaseConfig config = new FirebaseConfig();

    @BeforeAll
    static void generaCredenziali() throws Exception {
        KeyPairGenerator generatore = KeyPairGenerator.getInstance("RSA");
        generatore.initialize(2048);
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generatore.generateKeyPair().getPrivate().getEncoded());
        String pem = ("-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n").replace("\n", "\\n");
        credenzialiFinte = "{\"type\":\"service_account\",\"project_id\":\"eruplan-test\",\"private_key_id\":\"test\","
                + "\"private_key\":\"" + pem + "\","
                + "\"client_email\":\"test@eruplan-test.iam.gserviceaccount.com\",\"client_id\":\"1\","
                + "\"token_uri\":\"https://oauth2.googleapis.com/token\"}";
    }

    /** FirebaseApp è uno stato globale della JVM: lo azzeriamo prima e dopo ogni test (Independent). */
    @BeforeEach
    @AfterEach
    void eliminaAppFirebase() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    @DisplayName("Credenziali valide: crea l'app Firebase di default")
    void credenzialiValide() {
        ReflectionTestUtils.setField(config, "firebaseCredentialsJson", credenzialiFinte);

        FirebaseApp app = config.firebaseApp();

        assertNotNull(app);
        assertEquals(FirebaseApp.DEFAULT_APP_NAME, app.getName());
        assertEquals(1, FirebaseApp.getApps().size());
    }

    @Test
    @DisplayName("App già inizializzata: restituisce quella esistente senza crearne un'altra")
    void giaInizializzata() {
        ReflectionTestUtils.setField(config, "firebaseCredentialsJson", credenzialiFinte);
        FirebaseApp prima = config.firebaseApp();

        // Le credenziali non vengono nemmeno lette se l'app esiste già
        ReflectionTestUtils.setField(config, "firebaseCredentialsJson", "non usate");
        FirebaseApp seconda = config.firebaseApp();

        assertSame(prima, seconda);
        assertEquals(1, FirebaseApp.getApps().size());
    }

    @Test
    @DisplayName("Credenziali non valide: l'avvio fallisce con un messaggio chiaro e la causa originale")
    void credenzialiNonValide() {
        ReflectionTestUtils.setField(config, "firebaseCredentialsJson", "{\"type\":\"service_account\"}");

        RuntimeException e = assertThrows(RuntimeException.class, config::firebaseApp);

        assertEquals("Impossibile inizializzare FirebaseApp", e.getMessage());
        assertNotNull(e.getCause(), "la causa va conservata per capire cosa non va nelle credenziali");
        assertTrue(FirebaseApp.getApps().isEmpty());
    }
}
