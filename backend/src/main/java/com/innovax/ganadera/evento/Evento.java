package com.innovax.ganadera.evento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.common.tenant.TenantEntity;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un hecho registrado: sobre un animal, sobre un lote o sobre el establecimiento (animal y lote null).
 * Los datos propios de cada tipo van en {@link #datos} como JSON. No se modifica: si está mal, se
 * anula ({@link #anular()}) y se carga de nuevo.
 */
@Getter
@Entity
@Table(name = "evento")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evento extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @Column(name = "operacion_id", nullable = false, updatable = false)
    private UUID operacionId;

    @Convert(converter = TipoEvento.Converter.class)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoEvento tipo;

    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id", updatable = false)
    private Animal animal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", updatable = false)
    private Lote lote;

    @Column(nullable = false, length = 10000, updatable = false)
    private String datos;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Evento(Establecimiento establecimiento, UUID operacionId, TipoEvento tipo, LocalDate fecha,
                  Animal animal, Lote lote, String datos, Usuario usuario) {
        this.establecimiento = establecimiento;
        this.operacionId = operacionId;
        this.tipo = tipo;
        this.fecha = fecha;
        this.animal = animal;
        this.lote = lote;
        this.datos = datos;
        this.usuario = usuario;
    }

    public boolean isAnulado() {
        return deletedAt != null;
    }

    public void anular() {
        this.deletedAt = Instant.now();
    }
}
