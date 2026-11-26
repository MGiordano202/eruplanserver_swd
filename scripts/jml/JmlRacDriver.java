import it.unisa.eruplanserver.IS.Entity.GNF.NucleoFamiliareEntity;
import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaSicura;
import it.unisa.eruplanserver.IS.Utility.Utility;
import it.unisa.eruplanserver.IS.Utility.Validator;

import java.util.List;

/**
 * Driver per il Runtime Assertion Checker di OpenJML.
 * Le classi annotate vanno compilate con "openjml -rac" (vedi scripts/jml/verify.sh):
 * ogni violazione di una specifica JML viene stampata su stderr dal runtime di OpenJML.
 *
 * Gli scenari "OK" non devono produrre violazioni; quelli "ATTESA VIOLAZIONE"
 * chiamano i metodi fuori dal loro contratto per mostrare che RAC le rileva.
 */
public class JmlRacDriver {

    public static void main(String[] args) throws Exception {
        Punto a = new Punto(40.80, 14.20);
        Punto b = new Punto(40.81, 14.21);
        Punto c = new Punto(40.82, 14.20);
        Punto d = new Punto(40.81, 14.19);

        // ---------- Scenari validi: nessuna violazione ----------
        ok("Punto: costruttori, equals e hashCode", () -> {
            Punto copia = new Punto(40.80, 14.20);
            check(a.equals(copia) && a.hashCode() == copia.hashCode() && !a.equals(null));
            check(new Punto().getLat() == 0);
        });
        ok("ZonaSicura: costruttori", () -> check(new ZonaSicura(a, 100).getRaggio() == 100));
        ok("Validator: helper di lunghezza e null", () -> {
            check(Validator.isTroppoCorto(null, 2) && Validator.isTroppoCorto("A", 2));
            check(!Validator.isTroppoLungo("Mario", 30) && Validator.isTroppoLungo("x".repeat(31), 30));
            check(Validator.isCodiceFiscaleLengthValid("RSSMRA80A01H703X"));
            check(!Validator.isNomeValid(null) && !Validator.isCodiceFiscaleValid(""));
            check(Validator.isAssistenzaDefinita(true) && !Validator.isMinorenneDefinito(null));
        });
        ok("Validator.creaZoneSicure: raggi ai bordi 50 e 250", () ->
                Validator.creaZoneSicure(List.of(new ZonaSicura(a, 50), new ZonaSicura(b, 250))));
        ok("Validator.creaZonaPericolo: poligono chiuso A-B-C-D-A", () ->
                Validator.creaZonaPericolo(List.of(a, b, c, d, a)));
        ok("Validator: eccezioni previste dal contratto (exceptional_behavior)", () -> {
            expectIae(() -> Validator.creaZoneSicure(List.of(new ZonaSicura(a, 49))));
            expectIae(() -> Validator.creaZoneSicure(List.of(new ZonaSicura(a, 251))));
            expectIae(() -> Validator.creaZoneSicure(List.of()));
            expectIae(() -> Validator.creaZonaPericolo(List.of(a, b, a)));
            expectIae(() -> Validator.creaZonaPericolo(List.of(a, b, c, d)));
        });
        ok("Utility.encrypt: hash SHA-256 di 64 caratteri", () -> check(Utility.encrypt("Password1").length() == 64));
        ok("NucleoFamiliareEntity: posti 0 e 8 ammessi", () -> {
            NucleoFamiliareEntity n = NucleoFamiliareEntity.builder().hasVeicolo(false).numeroPostiVeicolo(0).build();
            n.setNumeroPostiVeicolo(8);
            check(n.getNumeroPostiVeicolo() == 8);
        });

        // ---------- Scenari fuori contratto: RAC deve segnalare la violazione ----------
        violation("Validator.checkConnessione con lista vuota (precondizione)", () ->
                Validator.checkConnessione(List.of()));
        violation("NucleoFamiliareEntity.setNumeroPostiVeicolo(-1) (precondizione + invariante)", () ->
                new NucleoFamiliareEntity().setNumeroPostiVeicolo(-1));
        violation("NucleoFamiliareEntity.builder() con 9 posti (invariante)", () ->
                NucleoFamiliareEntity.builder().hasVeicolo(true).numeroPostiVeicolo(9).build());
        violation("Utility.encrypt(null) (precondizione)", () -> Utility.encrypt(null));

        System.out.println("Driver RAC terminato.");
    }

    interface Step { void run() throws Exception; }

    static void ok(String nome, Step s) throws Exception {
        System.out.println("[OK atteso] " + nome);
        System.out.flush();
        s.run();
    }

    static void violation(String nome, Step s) {
        System.out.println("[ATTESA VIOLAZIONE] " + nome);
        System.out.flush();
        try {
            s.run();
        } catch (Throwable t) {
            // Dopo la violazione JML il codice prosegue e può fallire (es. IndexOutOfBounds, NullPointer)
            System.out.println("    eccezione Java successiva: " + t.getClass().getSimpleName());
        }
        System.out.flush();
    }

    static void expectIae(Step s) throws Exception {
        try {
            s.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("IllegalArgumentException attesa");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError("controllo fallito");
    }
}
