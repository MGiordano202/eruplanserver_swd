package it.unisa.eruplanserver.IS.Service.GPE;

import it.unisa.eruplanserver.IS.Entity.GPE.PianoEvacuazioneEntity;
import it.unisa.eruplanserver.IS.Entity.GPE.Punto;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaPericolo;
import it.unisa.eruplanserver.IS.Entity.GPE.ZonaSicura;
import it.unisa.eruplanserver.IS.Exception.GPE.GPEException;
import it.unisa.eruplanserver.IS.Repository.GPE.GPERepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test per {@link GPEServiceImpl#generaPiano}: validazione del nome, unicità,
 * contenuto del piano salvato e notifica di emergenza.
 * Il caso base di successo è anche in ZonaServiceTest (TC-W-17.7).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GPEServiceImpl - generazione del piano di evacuazione")
class GPEServiceImplTest {

    private static final String NOME_NON_VALIDO =
            "ERRORE - NOME PIANO NON VALIDO (Deve contenere solo lettere/numeri e avere 3-35 caratteri).";

    @Mock
    private GPERepository gpeRepository;

    @Mock
    private FirebaseService firebaseService;

    @InjectMocks
    private GPEServiceImpl service;

    private ZonaPericolo zonaPericolo;
    private ZonaSicura zonaSicura;

    @BeforeEach
    void setUp() {
        zonaPericolo = new ZonaPericolo();
        zonaPericolo.setPunti(List.of(
                new Punto(40.82, 14.42), new Punto(40.83, 14.43),
                new Punto(40.81, 14.44), new Punto(40.82, 14.42)));
        zonaSicura = new ZonaSicura(new Punto(40.85, 14.26), 120);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab", "Piano-Vesuvio", "Piano@1", "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8"})
    @DisplayName("Nome non valido: GPEException, nessun salvataggio e nessuna notifica")
    void nomeNonValido(String nome) {
        GPEException e = assertThrows(GPEException.class, () -> service.generaPiano(nome, zonaPericolo, zonaSicura));

        assertEquals(NOME_NON_VALIDO, e.getMessage());
        verifyNoInteractions(gpeRepository, firebaseService);
    }

    @Test
    @DisplayName("Nome già usato: GPEException, nessun salvataggio e nessuna notifica")
    void nomeDuplicato() {
        when(gpeRepository.existsByNome("Piano Vesuvio")).thenReturn(true);

        GPEException e = assertThrows(GPEException.class,
                () -> service.generaPiano("Piano Vesuvio", zonaPericolo, zonaSicura));

        assertEquals("ERRORE - ESISTE GIÀ UN PIANO CON QUESTO NOME.", e.getMessage());
        verify(gpeRepository, never()).save(any());
        verifyNoInteractions(firebaseService);
    }

    @Test
    @DisplayName("Dati validi: salva il piano ATTIVO con le zone ricevute e la data di creazione")
    void salvaPiano() throws GPEException {
        when(gpeRepository.existsByNome("Piano Vesuvio")).thenReturn(false);
        LocalDateTime prima = LocalDateTime.now();

        service.generaPiano("Piano Vesuvio", zonaPericolo, zonaSicura);

        LocalDateTime dopo = LocalDateTime.now();
        ArgumentCaptor<PianoEvacuazioneEntity> captor = ArgumentCaptor.forClass(PianoEvacuazioneEntity.class);
        verify(gpeRepository).save(captor.capture());
        PianoEvacuazioneEntity piano = captor.getValue();
        assertAll(
                () -> assertEquals("Piano Vesuvio", piano.getNome()),
                () -> assertEquals("ATTIVO", piano.getStato()),
                () -> assertSame(zonaPericolo, piano.getZonaPericolo()),
                () -> assertSame(zonaSicura, piano.getZoneSicure()),
                () -> assertNotNull(piano.getDataCreazione()),
                () -> assertFalse(piano.getDataCreazione().isBefore(prima)),
                () -> assertFalse(piano.getDataCreazione().isAfter(dopo)));
    }

    @Test
    @DisplayName("Dati validi: prima salva, poi invia la notifica sul topic 'emergenza' con il nome del piano")
    void notificaDopoSalvataggio() throws GPEException {
        when(gpeRepository.existsByNome("Piano Vesuvio")).thenReturn(false);

        service.generaPiano("Piano Vesuvio", zonaPericolo, zonaSicura);

        InOrder ordine = inOrder(gpeRepository, firebaseService);
        ordine.verify(gpeRepository).save(any(PianoEvacuazioneEntity.class));
        ordine.verify(firebaseService).inviaNotificaBroadcast(
                "⚠️ ALLERTA EVACUAZIONE ⚠️",
                "È stato attivato il piano: Piano Vesuvio. Controlla il tuo percorso!",
                "emergenza");
        verifyNoMoreInteractions(firebaseService);
    }

    @Test
    @DisplayName("Nome con spazi attorno: controllo duplicati e salvataggio usano il nome ripulito")
    void nomeConSpazi() throws GPEException {
        // Il Validator accetta "  Piano Vesuvio  " perché ignora gli spazi esterni:
        // senza ripulirlo si potrebbe creare un duplicato di "Piano Vesuvio"
        when(gpeRepository.existsByNome("Piano Vesuvio")).thenReturn(false);

        service.generaPiano("  Piano Vesuvio  ", zonaPericolo, zonaSicura);

        ArgumentCaptor<PianoEvacuazioneEntity> captor = ArgumentCaptor.forClass(PianoEvacuazioneEntity.class);
        verify(gpeRepository).save(captor.capture());
        assertEquals("Piano Vesuvio", captor.getValue().getNome());
    }

    @Test
    @DisplayName("Nome valido lungo 35 caratteri dopo la pulizia: rientra nella colonna del database (35)")
    void nomeLungoConSpazi() throws GPEException {
        String nome35 = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r";
        when(gpeRepository.existsByNome(nome35)).thenReturn(false);

        service.generaPiano("   " + nome35 + "   ", zonaPericolo, zonaSicura);

        ArgumentCaptor<PianoEvacuazioneEntity> captor = ArgumentCaptor.forClass(PianoEvacuazioneEntity.class);
        verify(gpeRepository).save(captor.capture());
        assertTrue(captor.getValue().getNome().length() <= 35, "nome troppo lungo per la colonna: " + captor.getValue().getNome().length());
    }

    @Test
    @DisplayName("Notifica fallita: il piano resta salvato e generaPiano non lancia eccezioni")
    void notificaFallita() {
        when(gpeRepository.existsByNome("Piano Vesuvio")).thenReturn(false);
        when(firebaseService.inviaNotificaBroadcast(anyString(), anyString(), anyString()))
                .thenReturn("Errore nell'invio: rete assente");

        assertDoesNotThrow(() -> service.generaPiano("Piano Vesuvio", zonaPericolo, zonaSicura));
        verify(gpeRepository).save(any(PianoEvacuazioneEntity.class));
    }

    @Test
    @DisplayName("Se il salvataggio fallisce, la notifica non parte")
    void salvataggioFallito() {
        when(gpeRepository.existsByNome("Piano Vesuvio")).thenReturn(false);
        when(gpeRepository.save(any())).thenThrow(new RuntimeException("DB non disponibile"));

        assertThrows(RuntimeException.class, () -> service.generaPiano("Piano Vesuvio", zonaPericolo, zonaSicura));
        verifyNoInteractions(firebaseService);
    }
}
