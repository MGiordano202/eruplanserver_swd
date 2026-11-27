package it.unisa.eruplanserver.IS.Utility;

import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaSicura;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Validator {

    /*
     * REGEX E PATTERN it.unisa.eruplanserver.Repository.GUM
     */
    private static final /*@ non_null @*/ String SESSO_REGEX = "^[MF]$";
    private static final /*@ non_null @*/ Pattern SESSO_PATTERN = Pattern.compile(SESSO_REGEX);

    /*
     * REGEX E PATTERN GPE (Nuovi)
     */
    private static final /*@ non_null @*/ String NOME_PIANO_REGEX = "^[a-zA-Z0-9\\s]{3,35}$";
    private static final /*@ non_null @*/ Pattern NOME_PIANO_PATTERN = Pattern.compile(NOME_PIANO_REGEX);

    // REGEX e PATTERN Indirizzi
    private static final /*@ non_null @*/ String CAP_REGEX = "^\\d{5}$";
    private static final /*@ non_null @*/ String CIVICO_REGEX = "^[0-9a-zA-Z/\\s]{1,6}$"; //
    private static final /*@ non_null @*/ Pattern CAP_PATTERN = Pattern.compile(CAP_REGEX);
    private static final /*@ non_null @*/ Pattern CIVICO_PATTERN = Pattern.compile(CIVICO_REGEX);
    private static final /*@ non_null @*/ String PAESE_REGEX = "^\\p{L}{4,40}$";
    private static final /*@ non_null @*/ Pattern PAESE_PATTERN = Pattern.compile(PAESE_REGEX);
    private static final /*@ non_null @*/ String PROVINCIA_REGEX = "^\\p{L}{4,20}$";
    private static final /*@ non_null @*/ Pattern PROVINCIA_PATTERN = Pattern.compile(PROVINCIA_REGEX);
    private static final /*@ non_null @*/ String REGIONE_REGEX = "^\\p{L}{5,25}$";
    private static final /*@ non_null @*/ Pattern REGIONE_PATTERN = Pattern.compile(REGIONE_REGEX);
    private static final /*@ non_null @*/ String COMUNE_REGEX = "^\\p{L}{2,40}$";
    private static final /*@ non_null @*/ Pattern COMUNE_PATTERN = Pattern.compile(COMUNE_REGEX);
    private static final /*@ non_null @*/ String VIA_REGEX = "^[a-zA-Z0-9\\s]{1,40}$";
    private static final /*@ non_null @*/ Pattern VIA_PATTERN = Pattern.compile(VIA_REGEX);

    /*
     * REGEX E PATTERN COMUNI
     */
    private static final /*@ non_null @*/ String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";
    private static final /*@ non_null @*/ Pattern PASSWORD_PATTERN = Pattern.compile(PASSWORD_REGEX);
    private static final /*@ non_null @*/ String NOME_PERSONA_REGEX = "^[a-zA-Z\\s]{2,30}$";
    private static final /*@ non_null @*/ Pattern NOME_PERSONA_PATTERN = Pattern.compile(NOME_PERSONA_REGEX);
    private static final /*@ non_null @*/ String CODICE_FISCALE_REGEX = "^[A-Za-z0-9]{16}$";
    private static final /*@ non_null @*/ Pattern CODICE_FISCALE_PATTERN = Pattern.compile(CODICE_FISCALE_REGEX);
    private static final /*@ non_null @*/ String COGNOME_PERSONA_REGEX="^[a-zA-Z\\s]{2,30}$";
    private static final /*@ non_null @*/ Pattern COGNOME_PERSONA_PATTERN = Pattern.compile(COGNOME_PERSONA_REGEX);

    // Nuovo pattern per TD-M-20: 16 caratteri, solo A-Za-z e cifre 1-9 (ZERO ESCLUSO)
    private static final /*@ non_null @*/ String CODICE_FISCALE_CHARS_REGEX = "^[A-Za-z1-9]{16}$";
    private static final /*@ non_null @*/ Pattern CODICE_FISCALE_CHARS_PATTERN = Pattern.compile(CODICE_FISCALE_CHARS_REGEX);

    private static final /*@ non_null @*/ DateTimeFormatter DATA_NASCITA_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /*
     * METODI DI VALIDAZIONE
     */
    // Le regex non sono modellabili in JML: il contratto fissa solo il caso nullo/vuoto
    /*@ public normal_behavior
      @   ensures (nome == null || nome.length() == 0) ==> !\result;
      @*/
    public static boolean isNomeValid(/*@ nullable @*/ String nome) {
        if (nome == null || nome.compareTo("") == 0) return false;
        boolean valido = corrisponde(NOME_PERSONA_PATTERN, nome);
        //@ assume nome.length() > 0; // compareTo("") != 0 implica una stringa non vuota
        return valido;
    }

    /*@ public normal_behavior
      @   ensures (cf == null || cf.length() == 0) ==> !\result;
      @*/
    public static boolean isCodiceFiscaleValid(/*@ nullable @*/ String cf) {
        if (cf == null || cf.compareTo("") == 0) return false;
        boolean valido = corrisponde(CODICE_FISCALE_PATTERN, cf);
        //@ assume cf.length() > 0; // compareTo("") != 0 implica una stringa non vuota
        return valido;
    }

    /*@ public normal_behavior
      @   ensures (sesso == null || sesso.length() == 0) ==> !\result;
      @*/
    public static boolean isSessoValid(/*@ nullable @*/ String sesso) {
        if (sesso == null || sesso.compareTo("") == 0) return false;
        boolean valido = corrisponde(SESSO_PATTERN, sesso);
        //@ assume sesso.length() > 0; // compareTo("") != 0 implica una stringa non vuota
        return valido;
    }

    /*@ public normal_behavior
      @   ensures (password == null || password.length() == 0) ==> !\result;
      @*/
    public static boolean isPasswordValid(/*@ nullable @*/ String password) {
        if (password == null || password.compareTo("") == 0) return false;
        boolean valido = corrisponde(PASSWORD_PATTERN, password);
        //@ assume password.length() > 0; // compareTo("") != 0 implica una stringa non vuota
        return valido;
    }

    /*@ public normal_behavior
      @   ensures (cognome == null || cognome.length() == 0) ==> !\result;
      @*/
    public static boolean isCognomeValid(/*@ nullable @*/ String cognome){
        if (cognome == null || cognome.compareTo("") == 0) return false;
        boolean valido = corrisponde(COGNOME_PERSONA_PATTERN, cognome);
        //@ assume cognome.length() > 0; // compareTo("") != 0 implica una stringa non vuota
        return valido;
    }

    /*@ public normal_behavior
      @   ensures data == null ==> !\result;
      @*/
    public static boolean isDataNascitaValid(/*@ nullable @*/ LocalDate data) {
        if (data == null) return false;
        try {
            return data.isBefore(LocalDate.now());
        } catch (RuntimeException e) {
            // java.time.LocalDate non ha specifiche in OpenJML: ipotesi che now() e isBefore() non lancino eccezioni
            //@ assume false;
            throw e;
        }
    }

    /**
     * Verifica se il nome del piano di evacuazione è valido.
     *
     * @param nomePiano Il nome da verificare
     * @return true se valido, false altrimenti
     */
    /*@ public normal_behavior
      @   ensures nomePiano == null ==> !\result;
      @*/
    public static boolean isNomePianoValid(/*@ nullable @*/ String nomePiano) {
        if (nomePiano == null || nomePiano.trim().isEmpty()) {
            return false;
        }
        return corrisponde(NOME_PIANO_PATTERN, nomePiano.trim());
    }

    // Matcher.matches() non ha specifica in OpenJML, quindi l'ESC dovrebbe supporre che lanci qualsiasi eccezione.
    // L'assume dichiara l'ipotesi che una regex già compilata, applicata a una stringa non nulla, non ne lanci.
    /*@ private normal_behavior
      @   requires pattern != null && input != null;
      @*/
    private static boolean corrisponde(Pattern pattern, String input) {
        try {
            return pattern.matcher(input).matches();
        } catch (RuntimeException e) {
            //@ assume false;
            throw e;
        }
    }

    public static boolean isIndirizzoValid(String via, String civico, String comune, String cap, String provincia, String regione, String paese) {
        if (via == null || !VIA_PATTERN.matcher(via).matches()) return false;
        if (civico == null || !CIVICO_PATTERN.matcher(civico).matches()) return false;
        if (comune == null || !COMUNE_PATTERN.matcher(comune).matches()) return false;
        if (cap == null || !CAP_PATTERN.matcher(cap).matches()) return false;
        if (provincia == null || !PROVINCIA_PATTERN.matcher(provincia).matches()) return false;
        if (regione == null || !REGIONE_PATTERN.matcher(regione).matches()) return false;
        if (paese == null || !PAESE_PATTERN.matcher(paese).matches()) return false;

        return true;
    }

    /**
     * Valida il formato della data di nascita nel formato dd/MM/yyyy
     *
     * @param data La data in formato stringa
     * @return true se il formato è valido, false altrimenti
     */
    public static boolean isDataNascitaFormatoValid(String data) {
        if (data == null || data.trim().isEmpty()) {
            return false;
        }
        try {
            // STRICT rifiuta le date inesistenti (31/02, 29/02 negli anni non bisestili)
            // invece di spostarle sull'ultimo giorno valido del mese; con STRICT l'anno va scritto "uuuu"
            LocalDate.parse(data, DATA_NASCITA_FORMATTER);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Valida se il campo assistenza è definito (non null)
     *
     * @param assistenza Il valore boolean del campo assistenza
     * @return true se il campo è stato impostato, false altrimenti
     */
    /*@ public normal_behavior
      @   ensures \result <==> assistenza != null;
      @   pure
      @*/
    public static boolean isAssistenzaDefinita(/*@ nullable @*/ Boolean assistenza) {
        return assistenza != null;
    }

    /**
     * Valida se il campo minorenne è definito (non null)
     *
     * @param minorenne Il valore boolean del campo minorenne
     * @return true se il campo è stato impostato, false altrimenti
     */
    /*@ public normal_behavior
      @   ensures \result <==> minorenne != null;
      @   pure
      @*/
    public static boolean isMinorenneDefinito(/*@ nullable @*/ Boolean minorenne) {
        return minorenne != null;
    }


    // Ogni zona sicura deve avere un raggio compreso tra 50 e 250 metri
    /*@ public normal_behavior
      @   requires zone != null && !zone.isEmpty();
      @   requires (\forall int i; 0 <= i && i < zone.size();
      @               zone.get(i) != null && 50 <= zone.get(i).raggio && zone.get(i).raggio <= 250);
      @   assignable \nothing;
      @ also public exceptional_behavior
      @   requires zone == null || zone.isEmpty();
      @   assignable \nothing;
      @   signals_only IllegalArgumentException;
      @ also public exceptional_behavior
      @   requires zone != null && !zone.isEmpty();
      @   requires (\forall int i; 0 <= i && i < zone.size(); zone.get(i) != null);
      @   requires (\exists int i; 0 <= i && i < zone.size();
      @               zone.get(i).raggio < 50 || zone.get(i).raggio > 250);
      @   assignable \nothing;
      @   signals_only IllegalArgumentException;
      @*/
    public static void creaZoneSicure(/*@ nullable @*/ List<ZonaSicura> zone) throws IllegalArgumentException {
        if (zone == null || zone.isEmpty()) throw new IllegalArgumentException("La creazione della zona sicura non viene effettuata dato che per il campo \"ListaZoneSicure\" non è stata definita nessuna coordinata.");

        // Ciclo con indice (stesso comportamento del for-each su una List): l'invariante di ciclo
        // può riferirsi a zone.get(k), mentre l'iteratore del for-each non è modellato da ESC
        //@ maintaining 0 <= i && i <= zone.size();
        //@ maintaining (\forall int k; 0 <= k && k < i; 50 <= zone.get(k).raggio && zone.get(k).raggio <= 250);
        //@ decreasing zone.size() - i;
        for (int i = 0; i < zone.size(); i++) {
            ZonaSicura z = zone.get(i);
            if (z.getRaggio() < 50) {
                throw new IllegalArgumentException("La creazione della zona sicura non viene effettuata dato che per il campo \"ListaZoneSicure\" per uno dei punti il raggio è troppo corto.");
            }
            if (z.getRaggio() > 250) {
                throw new IllegalArgumentException("La creazione della zona sicura non viene effettuata dato che per il campo \"ListaZoneSicure\" per uno dei punti il raggio è troppo grande.");
            }
        }
    }

    // Con meno di 4 punti il poligono non è valido; negli altri casi l'unica eccezione possibile è IllegalArgumentException
    /*@ public exceptional_behavior
      @   requires punti == null || punti.size() <= 3;
      @   assignable \nothing;
      @   signals_only IllegalArgumentException;
      @ also public behavior
      @   requires punti != null && punti.size() > 3;
      @   requires (\forall int i; 0 <= i && i < punti.size(); punti.get(i) != null);
      @   assignable \nothing;
      @   signals_only IllegalArgumentException;
      @*/
    public static void creaZonaPericolo(/*@ nullable @*/ List<Punto> punti) throws IllegalArgumentException {
        if (punti == null || punti.size() <= 3) {
            throw new IllegalArgumentException("La creazione della zona di pericolo non viene effettuata dato che il campo “ZonaPericolo” è composto da un numero di punti troppo basso.");
        }
        checkConnessione(punti);

        // Il poligono è chiuso: a parte il punto di chiusura, nessun punto deve ripetersi
        Set<Punto> puntiDistinti = new HashSet<>(punti.subList(0, punti.size() - 1));
        if (puntiDistinti.size() != punti.size() - 1) {
            throw new IllegalArgumentException("La creazione della zona di pericolo non viene effettuata dato che il campo “ZonaPericolo” contiene punti ripetuti.");
        }
    }

    // Precondizione: lista non vuota e senza null, altrimenti get(0) e equals fallirebbero
    /*@ public behavior
      @   requires punti != null && punti.size() > 0;
      @   requires (\forall int i; 0 <= i && i < punti.size(); punti.get(i) != null);
      @   assignable \nothing;
      @   signals_only IllegalArgumentException;
      @*/
    public static void checkConnessione(List<Punto> punti) throws IllegalArgumentException {
        Punto primo = punti.get(0);
        Punto ultimo = punti.get(punti.size() - 1);

        // Se il primo e l'ultimo punto NON coincidono, il poligono è aperto.
        // Dobbiamo capire PERCHÉ è aperto per dare il messaggio giusto.
        if (!primo.equals(ultimo)) {

            Set<Punto> set = new HashSet<>(punti);

            // Caso TC-W-17.3: Nessun punto è ripetuto (Set size == List size).
            // Esempio: A -> B -> C -> D. Il primo punto "A" non è collegato a nessun altro (D).
            if (set.size() == punti.size()) {
                throw new IllegalArgumentException("La creazione della zona di pericolo non viene effettuata dato che nel campo “ZonaPericolo” il primo punto non è collegato a nessun altro.");
            }

            // Caso TC-W-17.2: Ci sono punti ripetuti, ma l'ultimo non è il primo.
            // Esempio: A -> B -> C -> B. Il poligono si chiude su B, non su A.
            else {
                throw new IllegalArgumentException("La creazione della zona di pericolo non viene effettuata dato che nel campo “ZonaPericolo” l’ultimo punto della lista non è collegato al primo.");
            }
        }
    }

    /*@ public normal_behavior
      @   ensures \result <==> (cf != null && cf.length() == 16);
      @   pure
      @*/
    public static boolean isCodiceFiscaleLengthValid(/*@ nullable @*/ String cf) {
        return cf != null && cf.length() == 16;
    }

    public static boolean isCodiceFiscaleCharactersValid(String cf) {
        if (cf == null) return false;
        return CODICE_FISCALE_CHARS_PATTERN.matcher(cf).matches();
    }

    // ---------------------------------------------------------
    //  NUOVI METODI HELPER PER I TEST CASE (Lunghezza Specifica)
    // ---------------------------------------------------------

    /*@ public normal_behavior
      @   ensures \result <==> (input == null || input.length() < minLength);
      @   pure
      @*/
    public static boolean isTroppoCorto(/*@ nullable @*/ String input, int minLength) {
        return input == null || input.length() < minLength;
    }

    /*@ public normal_behavior
      @   ensures \result <==> (input != null && input.length() > maxLength);
      @   pure
      @*/
    public static boolean isTroppoLungo(/*@ nullable @*/ String input, int maxLength) {
        return input != null && input.length() > maxLength;
    }
}