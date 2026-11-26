package it.unisa.eruplanserver.IS.Utility;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class Utility {

    /**
     * Cripta una stringa.
     *
     * @param string La stringa da criptare.
     * @return Una rappresentazione esadecimale dell'hash della stringa.
     * @throws NoSuchAlgorithmException Se l'algoritmo di hashing non è disponibile.
     */
    /*@ public normal_behavior
      @   requires string != null;
      @   ensures \result != null && \result.length() >= 64;
      @*/
    public static String encrypt(String string) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        // Charset esplicito: stesso risultato del default UTF-8 di Java 18+, ma indipendente dalla piattaforma
        // (getBytes() senza argomenti manda anche in crash il RAC di OpenJML 21.0.29)
        byte[] message = md.digest(string.getBytes(StandardCharsets.UTF_8));
        BigInteger no = new BigInteger(1, message);
        String cryptedString = no.toString(16);

        //@ maintaining cryptedString != null;
        //@ decreasing 64 - cryptedString.length();
        while (cryptedString.length() < 64) {
            cryptedString = "0" + cryptedString;
        }
        return cryptedString;
    }
}