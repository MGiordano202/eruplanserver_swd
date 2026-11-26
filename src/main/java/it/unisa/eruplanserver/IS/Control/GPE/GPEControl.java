package it.unisa.eruplanserver.IS.Control.GPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaPericolo;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaSicura;
import it.unisa.eruplanserver.IS.Exception.GPE.GPEException;
import it.unisa.eruplanserver.IS.Service.GPE.GPEService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RestController
@RequestMapping("/gestorePiani")
public class GPEControl {

    @Autowired
    private GPEService gpeService;

    private static final Logger logger = LoggerFactory.getLogger(GPEControl.class);

    // ObjectMapper è thread-safe: una sola istanza per tutte le richieste
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Gestisce la richiesta di generazione di un piano di evacuazione.
     */
    @PostMapping("/genera")
    public void generaPiano(@RequestBody String body, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.info("POST /gestorePiani/genera called - body: {} - sessionId: {}", body, (request.getSession(false) != null) ? request.getSession(false).getId() : "no-session");
        JSONParser parser = new JSONParser();
        try {
            // Verifica sessione operatore (GUW)

            if (request.getSession().getAttribute("isOperatore") == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Accesso negato: Operatore non loggato.");
                return;
            }

            JSONObject json = (JSONObject) parser.parse(body);
            String nome = (String) json.get("nome");

            // json-simple restituisce le zone come JSONObject (una Map), non come ZonaPericolo/ZonaSicura:
            // un cast diretto lancia ClassCastException, quindi le convertiamo con Jackson
            ZonaPericolo zonaPericolo = converti(json.get("zonaPericolo"), ZonaPericolo.class);
            ZonaSicura zoneSicure = converti(json.get("zoneSicure"), ZonaSicura.class);
            gpeService.generaPiano(nome, zonaPericolo, zoneSicure);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"message\": \"Piano generato e notifiche inviate con successo.\"}");

        } catch (ParseException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Errore nel parsing del JSON.");
        } catch (GPEException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalArgumentException e) {
            // Zone presenti ma con una struttura che non corrisponde a ZonaPericolo/ZonaSicura
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Formato delle zone non valido.");
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore generico del server.");
        }
    }

    /** Converte un valore letto da json-simple nell'oggetto richiesto; null resta null. */
    private static <T> T converti(Object valore, Class<T> tipo) {
        return (valore == null) ? null : OBJECT_MAPPER.convertValue(valore, tipo);
    }
}