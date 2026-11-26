package it.unisa.eruplanserver.IS.Entity.GPE;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

// Getter e setter servono a Jackson per salvare il punto come JSON (ZonaPericoloConverter, ZonaSicuraConverter)
@Getter
@Setter
public class Punto {
    // spec_public: i campi restano package-private in Java ma sono visibili nelle specifiche pubbliche
    /*@ spec_public @*/ double lat;
    /*@ spec_public @*/ double lon;

    /*@ public normal_behavior
      @   ensures this.lat == 0 && this.lon == 0;
      @   pure
      @*/
    public Punto(){}

    // Con i double "==" è falso se il valore è NaN (NaN != NaN): la postcondizione tratta il caso a parte
    /*@ public normal_behavior
      @   ensures this.lat == lat || (Double.isNaN(lat) && Double.isNaN(this.lat));
      @   ensures this.lon == lon || (Double.isNaN(lon) && Double.isNaN(this.lon));
      @   pure
      @*/
    public Punto(double lat, double lon) { this.lat = lat; this.lon = lon; }

    // Contratto JML: uguaglianza riflessiva e false con null o con oggetti di altro tipo
    /*@ also public normal_behavior
      @   ensures o == this ==> \result;
      @   ensures !(o instanceof Punto) ==> !\result;
      @   pure
      @*/
    @Override
    public boolean equals(/*@ nullable @*/ Object o) {
        if (this == o) return true;
        if (!(o instanceof Punto)) return false;
        Punto punto = (Punto) o;
        return Double.compare(punto.lat, lat) == 0 && Double.compare(punto.lon, lon) == 0;
    }

    // Objects.hash non ha un modello JML: hashCode è escluso da ESC e controllato solo a runtime (RAC)
    //@ skipesc
    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }
}
