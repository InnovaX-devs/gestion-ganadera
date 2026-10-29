package com.innovax.ganadera.animal.categoria;

import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.common.tenant.TenantEntity;

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

/** Historial: cada vez que cambia la categoría de un animal, automática o manualmente, queda una fila. */
@Getter
@Entity
@Table(name = "cambio_categoria")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CambioCategoria extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "animal_id", nullable = false, updatable = false)
    private Animal animal;

    @Convert(converter = Categoria.Converter.class)
    @Column(name = "categoria_anterior", nullable = false, length = 20, updatable = false)
    private Categoria categoriaAnterior;

    @Convert(converter = Categoria.Converter.class)
    @Column(name = "categoria_nueva", nullable = false, length = 20, updatable = false)
    private Categoria categoriaNueva;

    @Convert(converter = MotivoCambio.Converter.class)
    @Column(nullable = false, length = 10, updatable = false)
    private MotivoCambio motivo;

    /** Fecha del hecho que provocó el cambio (destete, parto, pesaje, o el día que corrió el job). */
    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    /** Quién hizo el cambio manual. Null en los automáticos. */
    @Column(name = "usuario_id", updatable = false)
    private UUID usuarioId;

    public CambioCategoria(Animal animal, Categoria categoriaAnterior, Categoria categoriaNueva,
                           MotivoCambio motivo, LocalDate fecha, UUID usuarioId) {
        this.animal = animal;
        this.categoriaAnterior = categoriaAnterior;
        this.categoriaNueva = categoriaNueva;
        this.motivo = motivo;
        this.fecha = fecha;
        this.usuarioId = usuarioId;
    }
}
