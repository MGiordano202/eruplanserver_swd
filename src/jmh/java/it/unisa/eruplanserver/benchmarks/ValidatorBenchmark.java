package it.unisa.eruplanserver.benchmarks;

import it.unisa.eruplanserver.IS.Utility.Validator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Validazioni dei dati in ingresso ({@link Validator}), eseguite a ogni registrazione
 * e a ogni creazione o modifica del nucleo familiare.
 * Per la data di nascita misura sia il caso valido sia quello non valido,
 * che passa da un'eccezione catturata.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class ValidatorBenchmark {

    // Campi non final: con costanti la JVM potrebbe precalcolare il risultato
    public String codiceFiscale = "RSSMRA85T10A562S";
    public String password = "Password123";
    public String dataValida = "15/06/1990";
    public String dataInesistente = "31/02/1990";
    public String via = "Via Roma";
    public String civico = "12";
    public String comune = "Napoli";
    public String cap = "80100";
    public String provincia = "Napoli";
    public String regione = "Campania";
    public String paese = "Italia";

    @Benchmark
    public boolean codiceFiscale() {
        return Validator.isCodiceFiscaleValid(codiceFiscale);
    }

    @Benchmark
    public boolean password() {
        // Regex con tre lookahead: la più complessa del Validator
        return Validator.isPasswordValid(password);
    }

    @Benchmark
    public boolean indirizzo() {
        // Sette regex in sequenza, come nella creazione del nucleo familiare
        return Validator.isIndirizzoValid(via, civico, comune, cap, provincia, regione, paese);
    }

    @Benchmark
    public boolean dataNascitaValida() {
        return Validator.isDataNascitaFormatoValid(dataValida);
    }

    @Benchmark
    public boolean dataNascitaNonValida() {
        // LocalDate.parse lancia DateTimeParseException, catturata dal Validator
        return Validator.isDataNascitaFormatoValid(dataInesistente);
    }
}
