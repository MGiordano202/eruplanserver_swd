package it.unisa.eruplanserver.benchmarks;

import it.unisa.eruplanserver.IS.Utility.Utility;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Hash SHA-256 delle password ({@link Utility#encrypt}), eseguito a ogni registrazione e login.
 * Confronta l'implementazione attuale (BigInteger e zeri aggiunti in un ciclo) con
 * un'alternativa basata su {@link HexFormat} (Java 17+).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class EncryptBenchmark {

    /** Una password tipica e una lunga (passphrase). */
    @Param({"Password1", "UnaPassphraseMoltoLungaUsataDaChiNonVuoleRicordarePasswordComplicate2026"})
    public String password;

    @Setup
    public void verificaEquivalenza() throws NoSuchAlgorithmException {
        // Il confronto ha senso solo se le due versioni danno lo stesso risultato
        if (!Utility.encrypt(password).equals(encryptConHexFormat(password))) {
            throw new IllegalStateException("Le due implementazioni producono hash diversi per: " + password);
        }
    }

    @Benchmark
    public String encryptAttuale() throws NoSuchAlgorithmException {
        // Il valore restituito viene consumato da JMH: la JVM non può eliminare il calcolo
        return Utility.encrypt(password);
    }

    @Benchmark
    public String encryptHexFormat() throws NoSuchAlgorithmException {
        return encryptConHexFormat(password);
    }

    private static String encryptConHexFormat(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        // formatHex scrive sempre 2 cifre per byte: 64 caratteri senza ciclo di riempimento
        return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
