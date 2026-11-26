package it.unisa.eruplanserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit test per {@link EruplanServerApplication}: punto di avvio ed endpoint di benvenuto.
 * SpringApplication.run è simulato: l'applicazione non viene avviata davvero (niente database, niente Firebase).
 */
@DisplayName("EruplanServerApplication - avvio ed endpoint di benvenuto")
class EruplanServerApplicationTest {

    @Test
    @DisplayName("main avvia Spring Boot con la classe dell'applicazione e gli argomenti ricevuti")
    void mainAvviaSpring() {
        String[] argomenti = {"--server.port=0"};

        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            EruplanServerApplication.main(argomenti);

            spring.verify(() -> SpringApplication.run(EruplanServerApplication.class, argomenti));
        }
    }

    @Test
    @DisplayName("GET /utenti/ risponde con il messaggio di benvenuto (usato come controllo di vita)")
    void home() {
        assertEquals("Hello from Azure Web App - Java 21!", new EruplanServerApplication().home());
    }
}
