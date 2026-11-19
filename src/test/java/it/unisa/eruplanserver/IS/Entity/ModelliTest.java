package it.unisa.eruplanserver.IS.Entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unisa.eruplanserver.IS.Entity.GNF.AggiornaVeicoloRequest;
import it.unisa.eruplanserver.IS.Entity.GNF.CreazioneNucleoRequest;
import it.unisa.eruplanserver.IS.Entity.GNF.ResidenzaEntity;
import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Exception.GNF.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test per i modelli con logica scritta a mano (non generata da Lombok):
 * DTO delle richieste GNF, uguaglianza dei punti GPE e ValidationException.
 */
@DisplayName("Modelli - DTO delle richieste, Punto e ValidationException")
class ModelliTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("DTO delle richieste GNF")
    class Dto {

        @Test
        @DisplayName("AggiornaVeicoloRequest: letto dal JSON del client come fa Spring, oppure costruito direttamente")
        void aggiornaVeicolo() throws Exception {
            AggiornaVeicoloRequest daJson = mapper.readValue("{\"hasVeicolo\":true,\"numeroPostiVeicolo\":4}", AggiornaVeicoloRequest.class);
            AggiornaVeicoloRequest costruito = new AggiornaVeicoloRequest(true, 4);

            assertTrue(daJson.isHasVeicolo());
            assertEquals(4, daJson.getNumeroPostiVeicolo());
            assertEquals(daJson.isHasVeicolo(), costruito.isHasVeicolo());
            assertEquals(daJson.getNumeroPostiVeicolo(), costruito.getNumeroPostiVeicolo());
        }

        @Test
        @DisplayName("AggiornaVeicoloRequest: senza veicolo i posti possono mancare (null)")
        void senzaVeicolo() throws Exception {
            AggiornaVeicoloRequest daJson = mapper.readValue("{\"hasVeicolo\":false}", AggiornaVeicoloRequest.class);

            assertFalse(daJson.isHasVeicolo());
            assertNull(daJson.getNumeroPostiVeicolo());
        }

        @Test
        @DisplayName("CreazioneNucleoRequest: residenza annidata e dati del veicolo letti dal JSON, oppure costruiti direttamente")
        void creazioneNucleo() throws Exception {
            CreazioneNucleoRequest daJson = mapper.readValue(
                    "{\"residenza\":{\"viaPiazza\":\"Via Roma\",\"cap\":\"80100\"},\"hasVeicolo\":true,\"numeroPostiVeicolo\":5}",
                    CreazioneNucleoRequest.class);
            ResidenzaEntity residenza = ResidenzaEntity.builder().viaPiazza("Via Roma").cap("80100").build();
            CreazioneNucleoRequest costruito = new CreazioneNucleoRequest(residenza, true, 5);

            assertEquals("Via Roma", daJson.getResidenza().getViaPiazza());
            assertEquals("80100", daJson.getResidenza().getCap());
            assertTrue(daJson.isHasVeicolo());
            assertEquals(5, daJson.getNumeroPostiVeicolo());
            assertSame(residenza, costruito.getResidenza());
            assertTrue(costruito.isHasVeicolo());
            assertEquals(5, costruito.getNumeroPostiVeicolo());
        }
    }

    @Nested
    @DisplayName("Punto: uguaglianza usata dal Validator per trovare i punti ripetuti del poligono")
    class PuntoUguaglianza {

        @Test
        @DisplayName("Stesse coordinate: punti uguali con lo stesso hashCode")
        void stesseCoordinate() {
            Punto a = new Punto(40.82, 14.42);
            Punto b = new Punto(40.82, 14.42);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertEquals(a, a, "un punto è uguale a se stesso");
        }

        @Test
        @DisplayName("Latitudine o longitudine diversa, null o altro tipo: punti diversi")
        void diversi() {
            Punto a = new Punto(40.82, 14.42);

            assertNotEquals(a, new Punto(40.83, 14.42));
            assertNotEquals(a, new Punto(40.82, 14.43));
            assertNotEquals(a, null);
            assertNotEquals(a, "40.82,14.42");
        }

        @Test
        @DisplayName("In un HashSet i punti ripetuti contano una volta sola")
        void hashSet() {
            Set<Punto> punti = new HashSet<>(List.of(new Punto(1, 2), new Punto(1, 2), new Punto(2, 1)));

            assertEquals(2, punti.size());
        }
    }

    @Test
    @DisplayName("ValidationException conserva messaggio e causa originale")
    void validationException() {
        IllegalArgumentException causa = new IllegalArgumentException("data non valida");

        ValidationException e = new ValidationException("Formato data non valido", causa);

        assertEquals("Formato data non valido", e.getMessage());
        assertSame(causa, e.getCause());
    }
}
