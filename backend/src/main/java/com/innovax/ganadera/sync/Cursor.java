package com.innovax.ganadera.sync;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.innovax.ganadera.common.error.BusinessException;

/**
 * Hasta dónde recibió cambios el celular, por tabla: el último {@code (updated_at, id)} que se le
 * mandó. Viaja como texto opaco (JSON en base64): el cliente solo lo guarda y lo devuelve.
 *
 * <p>{@code corte} es el tope de {@code updated_at} de una vuelta de sincronización: mientras
 * {@code hayMas} sea true, todas las páginas usan el mismo, así no se mezclan cambios nuevos a
 * mitad de camino. Ver docs/sync.md.
 */
record Cursor(UUID establecimientoId, Instant corte, Map<Tabla, Posicion> posiciones) {

    private static final int VERSION = 1;
    static final UUID CERO = new UUID(0, 0);

    /** Las tablas que se sincronizan, en el orden en que se recorren. */
    enum Tabla {
        LOTE, RODEO, PRODUCTO, ANIMAL, EVENTO, BAJA
    }

    record Posicion(Instant updatedAt, UUID id) {
        static final Posicion INICIO = new Posicion(Instant.EPOCH, CERO);
    }

    static Cursor inicial(UUID establecimientoId) {
        return new Cursor(establecimientoId, null, new EnumMap<>(Tabla.class));
    }

    Posicion posicion(Tabla tabla) {
        return posiciones.getOrDefault(tabla, Posicion.INICIO);
    }

    String codificar(ObjectMapper json) {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("v", VERSION);
        raiz.put("e", establecimientoId.toString());
        if (corte != null) {
            raiz.put("cs", corte.getEpochSecond());
            raiz.put("cn", corte.getNano());
        }
        ObjectNode p = raiz.putObject("p");
        posiciones.forEach((tabla, pos) -> {
            ObjectNode n = p.putObject(tabla.name());
            n.put("s", pos.updatedAt().getEpochSecond());
            n.put("n", pos.updatedAt().getNano());
            n.put("i", pos.id().toString());
        });
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raiz.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws BusinessException si el cursor es ilegible o es de otro establecimiento: el cliente
     *     tiene que empezar de cero (sin cursor)
     */
    static Cursor decodificar(String texto, UUID establecimientoId, ObjectMapper json) {
        if (texto == null || texto.isBlank()) return inicial(establecimientoId);
        try {
            JsonNode raiz = json.readTree(Base64.getUrlDecoder().decode(texto));
            if (raiz.path("v").asInt() != VERSION
                    || !establecimientoId.toString().equals(raiz.path("e").asText())) {
                throw new BusinessException(INVALIDO);
            }
            Instant corte = raiz.hasNonNull("cs")
                ? Instant.ofEpochSecond(raiz.get("cs").asLong(), raiz.path("cn").asLong())
                : null;
            Map<Tabla, Posicion> posiciones = new EnumMap<>(Tabla.class);
            for (Tabla tabla : Tabla.values()) {
                JsonNode n = raiz.path("p").path(tabla.name());
                if (n.isMissingNode()) continue;
                posiciones.put(tabla, new Posicion(
                    Instant.ofEpochSecond(n.get("s").asLong(), n.get("n").asLong()), UUID.fromString(n.get("i").asText())));
            }
            return new Cursor(establecimientoId, corte, posiciones);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(INVALIDO);
        }
    }

    static final String INVALIDO = "El cursor de sincronización no es válido: sincronizá de nuevo sin cursor";
}
