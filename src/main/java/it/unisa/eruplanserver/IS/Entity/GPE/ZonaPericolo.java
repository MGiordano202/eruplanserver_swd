package it.unisa.eruplanserver.IS.Entity.GPE;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Value object stored as JSON inside PianoEvacuazioneEntity (see ZonaPericoloConverter).
 * It is not a JPA entity: mapping it with @Entity makes Hibernate fail at startup
 * because List<Punto> has no JDBC type.
 */
@Getter
@Setter
public class ZonaPericolo {
    private Long id;
    List<Punto> punti;
}
