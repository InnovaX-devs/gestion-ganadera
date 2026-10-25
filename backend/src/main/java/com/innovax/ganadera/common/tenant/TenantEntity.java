package com.innovax.ganadera.common.tenant;

import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Base de toda entidad de negocio (establecimiento, lote, animal, evento...).
 *
 * <p>Con {@link TenantId}, Hibernate:
 * <ul>
 *   <li>completa {@code organizacion_id} solo al insertar, con la organización del usuario logueado;</li>
 *   <li>agrega {@code organizacion_id = ?} solo en todas las consultas.</li>
 * </ul>
 * Por eso nunca hay que setear ni filtrar la organización a mano en servicios o repositorios.
 *
 * <p>La tabla tiene que tener la columna {@code organizacion_id UUID NOT NULL REFERENCES organizacion(id)}.
 */
@Getter
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "organizacion_id", nullable = false, updatable = false)
    private UUID organizacionId;
}
