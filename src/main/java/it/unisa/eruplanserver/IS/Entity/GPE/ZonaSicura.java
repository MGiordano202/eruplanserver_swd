package it.unisa.eruplanserver.IS.Entity.GPE;

import lombok.Getter;
import lombok.Setter;

// Getter e setter su tutti i campi: senza, Jackson salva solo il raggio e perde le coordinate (ZonaSicuraConverter)
@Getter
@Setter
public class ZonaSicura {

    private /*@ spec_public nullable @*/ Punto coordinate;
    /*@ spec_public @*/ double raggio;
    /*@ public normal_behavior
      @   ensures this.coordinate == coordinate;
      @   ensures this.raggio == raggio || (Double.isNaN(raggio) && Double.isNaN(this.raggio));
      @   pure
      @*/
    public ZonaSicura(/*@ nullable @*/ Punto coordinate, double raggio) {
        this.coordinate = coordinate;
        this.raggio = raggio;
    }

    /*@ public normal_behavior
      @   ensures this.coordinate == null && this.raggio == 0;
      @   pure
      @*/
    public ZonaSicura(){}

    // Getter scritto a mano (Lombok non lo genera) per dargli un contratto usabile da ESC in Validator.creaZoneSicure
    /*@ public normal_behavior
      @   ensures \result == raggio || (Double.isNaN(raggio) && Double.isNaN(\result));
      @   pure
      @*/
    public double getRaggio() {
        return raggio;
    }
}