package com.innovax.ganadera.sync;

import java.util.UUID;

import com.innovax.ganadera.common.tenant.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Algo que el celular tiene que borrar (ver {@link BajaSyncListener}). Solo se lee con JPA. */
@Getter
@Entity
@Table(name = "sync_baja")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncBaja extends TenantEntity {

    @Column(name = "establecimiento_id", nullable = false, updatable = false)
    private UUID establecimientoId;

    @Column(nullable = false, length = 20, updatable = false)
    private String entidad;

    @Column(name = "entidad_id", nullable = false, updatable = false)
    private UUID entidadId;
}
