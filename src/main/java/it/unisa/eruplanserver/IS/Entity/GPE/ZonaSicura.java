package it.unisa.eruplanserver.IS.Entity.GPE;

import lombok.Getter;
import lombok.Setter;

// Getter e setter su tutti i campi: senza, Jackson salva solo il raggio e perde le coordinate (ZonaSicuraConverter)
@Getter
@Setter
public class ZonaSicura {

    private Punto coordinate;
    double raggio;
    public ZonaSicura(Punto coordinate, double raggio) {
        this.coordinate = coordinate;
        this.raggio = raggio;
    }
    public ZonaSicura(){}
}