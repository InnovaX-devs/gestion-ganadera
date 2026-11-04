package com.innovax.ganadera.common.tenant;

import java.util.UUID;

/**
 * Usuario logueado. Es el "principal" que el filtro JWT (issue #7) pone en el SecurityContext a
 * partir del token. La organización sale siempre de acá, nunca de un parámetro del request.
 */
public record AuthenticatedUser(UUID userId, UUID organizacionId, String email) {
}
