package com.innovax.ganadera.common.tenant;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Le dice a Hibernate cuál es la organización actual para el filtro de {@code @TenantId}.
 *
 * <p>Sin usuario logueado devuelve {@link #SIN_ORGANIZACION} (UUID de ceros): las consultas no
 * devuelven nada y los inserts fallan por la foreign key a {@code organizacion}. Es decir, ante la
 * duda, no se ve ni se guarda nada.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    public static final UUID SIN_ORGANIZACION = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.organizacionIdActual().orElse(SIN_ORGANIZACION);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
