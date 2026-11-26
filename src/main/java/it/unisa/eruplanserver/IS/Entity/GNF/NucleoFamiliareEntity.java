package it.unisa.eruplanserver.IS.Entity.GNF;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.unisa.eruplanserver.IS.Entity.GUM.UREntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "nucleo_familiare")
public class NucleoFamiliareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- NUOVI CAMPI (RF-GNF.07) ---
    private boolean hasVeicolo;
    private /*@ spec_public @*/ int numeroPostiVeicolo;

    // Invariante JML: il numero di posti è compreso tra 0 (nessun veicolo) e 8
    //@ public invariant 0 <= numeroPostiVeicolo && numeroPostiVeicolo <= 8;

    // Collegamento alla nuova tabella Residenza (Indirizzo)
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "residenza_id", referencedColumnName = "id")
    private ResidenzaEntity residenza;

    // L'utente che ha creato il nucleo (Amministratore del nucleo)
    @OneToOne
    @JoinColumn(name = "admin_id", nullable = false)
    // L'admin punta di nuovo a questo nucleo: senza escludere quel campo il JSON va in ricorsione infinita
    @JsonIgnoreProperties("nucleoFamiliare")
    private UREntity admin;

    // Lista degli utenti registrati che fanno parte del nucleo
    @OneToMany(mappedBy = "nucleoFamiliare")
    @JsonIgnore
    private List<UREntity> membriRegistrati;

    // Lista dei membri aggiunti manualmente (RF-GNF.03)
    @OneToMany(mappedBy = "nucleoFamiliare", cascade = CascadeType.ALL)
    private List<MembroEntity> membriManuali;

    // RF-GNF.09: Lista dei luoghi sicuri/appoggi
    @OneToMany(mappedBy = "nucleoFamiliare", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AppoggioEntity> appoggi;

    // Setter scritto a mano (Lombok non lo genera): la precondizione JML protegge l'invariante sui posti
    /*@ public normal_behavior
      @   requires 0 <= numeroPostiVeicolo && numeroPostiVeicolo <= 8;
      @   assignable this.numeroPostiVeicolo;
      @   ensures this.numeroPostiVeicolo == numeroPostiVeicolo;
      @*/
    public void setNumeroPostiVeicolo(int numeroPostiVeicolo) {
        this.numeroPostiVeicolo = numeroPostiVeicolo;
    }
}