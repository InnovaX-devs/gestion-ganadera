package com.innovax.ganadera.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.innovax.ganadera.plan.dto.PlanResponse;

/**
 * Una cuenta vista desde el panel de administración.
 *
 * @param duenoEmail el primer usuario de la cuenta (quien la creó)
 * @param duenoPendiente la cuenta se creó desde el panel y el dueño todavía no eligió su contraseña
 * @param suspendida nadie de la cuenta puede entrar
 * @param plan       plan que rige, contratado, prueba y uso (lo mismo que ve la cuenta en Mi plan)
 */
public record OrganizacionAdminResponse(
    UUID id,
    String nombre,
    String duenoNombre,
    String duenoEmail,
    boolean duenoPendiente,
    boolean suspendida,
    Instant createdAt,
    PlanResponse plan
) {
}
