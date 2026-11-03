package com.innovax.ganadera.admin;

/**
 * Quién entró al panel de administración. No pertenece a ninguna organización: con este principal
 * el filtro multi-tenant no ve datos de nadie salvo dentro de {@code TenantContext.comoOrganizacion}.
 */
public record AdminAutenticado(String email) {
}
