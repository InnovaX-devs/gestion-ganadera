package com.innovax.ganadera.common.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Base de todas las entidades: id UUID y fechas de auditoría.
 *
 * <p>El id se genera en Java al crear el objeto (no en la base). Esto permite que más adelante la
 * app offline genere los ids en el celular. Como el id nunca es null, se implementa
 * {@link Persistable} para que Spring Data sepa cuándo la entidad es nueva y haga INSERT directo
 * en vez de un SELECT + MERGE.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean nuevo = true;

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostPersist
    @PostLoad
    void marcarComoPersistida() {
        this.nuevo = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((BaseEntity) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
