package it.unisa.eruplanserver.benchmarks;

import it.unisa.eruplanserver.IS.Utility.Utility;
import it.unisa.eruplanserver.IS.Utility.Validator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.DynamicHalt;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Gli stessi metodi di {@link EncryptBenchmark} e {@link ValidatorBenchmark}, ma senza @Warmup:
 * con @DynamicHalt è AMBER a decidere quando il riscaldamento è finito, interrogando
 * il modello di intelligenza artificiale del servizio jpt_service (localhost:5001).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
@DynamicHalt(model = "oscnn")
public class AmberWarmupBenchmark {

    public String password = "Password1";
    public String dataInesistente = "31/02/1990";

    @Benchmark
    public String encrypt() throws NoSuchAlgorithmException {
        return Utility.encrypt(password);
    }

    @Benchmark
    public boolean dataNascitaNonValida() {
        return Validator.isDataNascitaFormatoValid(dataInesistente);
    }
}
