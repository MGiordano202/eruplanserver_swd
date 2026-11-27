package it.unisa.eruplanserver.benchmarks;

import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaPericolo;
import it.unisa.eruplanserver.IS.Utility.Validator;
import it.unisa.eruplanserver.IS.Utility.ZonaPericoloConverter;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Zone di pericolo del piano di evacuazione: validazione del poligono
 * ({@link Validator#creaZonaPericolo}) e conversione da e verso JSON
 * ({@link ZonaPericoloConverter}), eseguita a ogni salvataggio e lettura dal database.
 * Il numero di punti varia per vedere come cresce il costo.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class ZonaPericoloBenchmark {

    /** Punti del poligono: minimo ammesso, zona realistica, zona molto dettagliata. */
    @Param({"4", "100", "1000"})
    public int numeroPunti;

    private final ZonaPericoloConverter converter = new ZonaPericoloConverter();
    private List<Punto> punti;
    private ZonaPericolo zona;
    private String json;

    @Setup
    public void preparaZona() {
        // Poligono chiuso intorno al Vesuvio: punti distinti su una circonferenza, l'ultimo coincide col primo
        punti = new ArrayList<>(numeroPunti);
        for (int i = 0; i < numeroPunti - 1; i++) {
            double angolo = 2 * Math.PI * i / (numeroPunti - 1);
            punti.add(new Punto(40.8217 + 0.05 * Math.sin(angolo), 14.4258 + 0.05 * Math.cos(angolo)));
        }
        punti.add(punti.get(0));

        zona = new ZonaPericolo();
        zona.setId(1L);
        zona.setPunti(punti);
        json = converter.convertToDatabaseColumn(zona);
    }

    @Benchmark
    public void validazionePoligono(Blackhole bh) {
        // Metodo void: il Blackhole consuma la lista e impedisce che la chiamata venga eliminata
        Validator.creaZonaPericolo(punti);
        bh.consume(punti);
    }

    @Benchmark
    public String versoJson() {
        return converter.convertToDatabaseColumn(zona);
    }

    @Benchmark
    public ZonaPericolo daJson() {
        return converter.convertToEntityAttribute(json);
    }
}
