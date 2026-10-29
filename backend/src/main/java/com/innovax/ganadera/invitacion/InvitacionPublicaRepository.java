package com.innovax.ganadera.invitacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.innovax.ganadera.membresia.Rol;

import lombok.RequiredArgsConstructor;

/**
 * Busca una invitación por su token <b>sin pasar por el filtro multi-tenant</b>: quien abre el link
 * todavía no está logueado, así que no hay organización contra la cual filtrar. Es seguro porque
 * el token es un secreto de 256 bits; sin él no se llega a ninguna fila. Por eso es JDBC directo
 * (las consultas nativas no se filtran, ver docs/decisiones/0002-multi-tenant.md) y devuelve
 * solo lo necesario.
 */
@Repository
@RequiredArgsConstructor
public class InvitacionPublicaRepository {

    private static final String SQL = """
        SELECT i.id, i.organizacion_id, i.establecimiento_id, i.email, i.rol,
               i.vence_at, i.aceptada_at, i.revocada_at,
               e.nombre AS establecimiento_nombre, o.nombre AS organizacion_nombre
        FROM invitacion i
        JOIN establecimiento e ON e.id = i.establecimiento_id
        JOIN organizacion o ON o.id = i.organizacion_id
        WHERE i.token_hash = ? AND e.activo = TRUE
        """;

    private static final RowMapper<InvitacionPublica> MAPPER = (rs, fila) -> new InvitacionPublica(
        rs.getObject("id", UUID.class),
        rs.getObject("organizacion_id", UUID.class),
        rs.getObject("establecimiento_id", UUID.class),
        rs.getString("email"),
        Rol.valueOf(rs.getString("rol")),
        instante(rs, "vence_at"),
        instante(rs, "aceptada_at"),
        instante(rs, "revocada_at"),
        rs.getString("establecimiento_nombre"),
        rs.getString("organizacion_nombre"));

    private final JdbcTemplate jdbc;

    public Optional<InvitacionPublica> buscarPorToken(String token) {
        return jdbc.query(SQL, MAPPER, TokensInvitacion.hash(token)).stream().findFirst();
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
