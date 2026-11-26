package it.unisa.eruplanserver.IS.Utility;

import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaPericolo;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaSicura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test per i converter JPA che salvano le zone del piano come JSON in una colonna TEXT.
 * Verificano che i dati sopravvivano al viaggio oggetto → database → oggetto.
 */
@DisplayName("Converter JSON delle zone del piano di evacuazione")
class ZonaConverterTest {

    @Nested
    @DisplayName("ZonaPericoloConverter")
    class Pericolo {

        private final ZonaPericoloConverter converter = new ZonaPericoloConverter();

        @Test
        @DisplayName("null viene salvato come null")
        void nullVersoDb() {
            assertNull(converter.convertToDatabaseColumn(null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Colonna null o vuota diventa null")
        void nullDaDb(String dbData) {
            assertNull(converter.convertToEntityAttribute(dbData));
        }

        @Test
        @DisplayName("Andata e ritorno conserva id e tutti i punti del poligono")
        void andataERitorno() {
            List<Punto> punti = List.of(
                    new Punto(40.82, 14.42), new Punto(40.83, 14.43),
                    new Punto(40.81, 14.44), new Punto(40.82, 14.42));
            ZonaPericolo zona = new ZonaPericolo();
            zona.setId(7L);
            zona.setPunti(punti);

            String json = converter.convertToDatabaseColumn(zona);
            ZonaPericolo letta = converter.convertToEntityAttribute(json);

            assertEquals(7L, letta.getId());
            assertEquals(punti, letta.getPunti());
        }

        @Test
        @DisplayName("Zona senza punti (lista vuota o null) sopravvive al salvataggio")
        void senzaPunti() {
            ZonaPericolo vuota = new ZonaPericolo();
            vuota.setPunti(List.of());
            assertEquals(List.of(), converter.convertToEntityAttribute(converter.convertToDatabaseColumn(vuota)).getPunti());

            ZonaPericolo nulla = new ZonaPericolo();
            assertNull(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(nulla)).getPunti());
        }

        @Test
        @DisplayName("Coordinate negative e con molte cifre decimali non perdono precisione")
        void precisione() {
            List<Punto> punti = List.of(new Punto(-33.868820123456, 151.209295987654), new Punto(0, -0.000001));
            ZonaPericolo zona = new ZonaPericolo();
            zona.setPunti(punti);

            assertEquals(punti, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(zona)).getPunti());
        }

        @Test
        @DisplayName("JSON non valido lancia RuntimeException con messaggio chiaro")
        void jsonNonValido() {
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> converter.convertToEntityAttribute("{non json"));
            assertEquals("Errore deserializzazione JSON ZonaPericolo", e.getMessage());
        }
    }

    @Nested
    @DisplayName("ZonaSicuraConverter")
    class Sicura {

        private final ZonaSicuraConverter converter = new ZonaSicuraConverter();

        @Test
        @DisplayName("null viene salvato come null")
        void nullVersoDb() {
            assertNull(converter.convertToDatabaseColumn(null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Colonna null o vuota diventa null")
        void nullDaDb(String dbData) {
            assertNull(converter.convertToEntityAttribute(dbData));
        }

        @Test
        @DisplayName("Andata e ritorno conserva raggio e coordinate del centro")
        void andataERitorno() {
            ZonaSicura letta = converter.convertToEntityAttribute(
                    converter.convertToDatabaseColumn(new ZonaSicura(new Punto(40.85, 14.26), 120)));

            assertEquals(120, letta.getRaggio());
            assertEquals(new Punto(40.85, 14.26), letta.getCoordinate());
        }

        @Test
        @DisplayName("Zona senza coordinate: il raggio resta, le coordinate restano null")
        void senzaCoordinate() {
            ZonaSicura letta = converter.convertToEntityAttribute(
                    converter.convertToDatabaseColumn(new ZonaSicura(null, 80)));

            assertEquals(80, letta.getRaggio());
            assertNull(letta.getCoordinate());
        }

        @Test
        @DisplayName("Il JSON salvato contiene anche le coordinate del centro")
        void coordinate() {
            String json = converter.convertToDatabaseColumn(new ZonaSicura(new Punto(40.85, 14.26), 120));

            assertAll(
                    () -> assertTrue(json.contains("40.85"), "latitudine persa: " + json),
                    () -> assertTrue(json.contains("14.26"), "longitudine persa: " + json));
        }

        @Test
        @DisplayName("JSON non valido lancia RuntimeException con messaggio chiaro")
        void jsonNonValido() {
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> converter.convertToEntityAttribute("{non json"));
            assertEquals("Errore durante la deserializzazione JSON di ZonaSicura", e.getMessage());
        }
    }
}
