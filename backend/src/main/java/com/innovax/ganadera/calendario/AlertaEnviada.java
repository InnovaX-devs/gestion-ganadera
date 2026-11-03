package com.innovax.ganadera.calendario;

import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.common.tenant.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ya se le mandó a ese usuario el aviso de ese establecimiento ese día (no se repite). */
@Getter
@Entity
@Table(name = "alerta_enviada")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaEnviada extends TenantEntity {

    @Column(name = "establecimiento_id", nullable = false, updatable = false)
    private UUID establecimientoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    public AlertaEnviada(UUID establecimientoId, UUID usuarioId, LocalDate fecha) {
        this.establecimientoId = establecimientoId;
        this.usuarioId = usuarioId;
        this.fecha = fecha;
    }
}
