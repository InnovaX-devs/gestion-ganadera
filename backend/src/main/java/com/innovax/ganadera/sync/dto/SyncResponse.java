package com.innovax.ganadera.sync.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.sanidad.TipoProducto;
import com.innovax.ganadera.sanidad.ViaAdministracion;

/**
 * Respuesta de {@code POST /api/sync}. Ver docs/sync.md.
 *
 * @param cursor para la próxima llamada (guardarlo solo si se aplicaron todos los cambios)
 * @param hayMas true: quedan cambios, llamar de nuevo con este cursor (sin operaciones)
 * @param cambios filas nuevas o modificadas: el celular las guarda por id (reemplazando la que tenga)
 * @param bajas filas que el celular tiene que borrar
 */
public record SyncResponse(
    String cursor,
    boolean hayMas,
    Instant servidorEn,
    List<ResultadoOperacion> resultados,
    Establecimiento establecimiento,
    Cambios cambios,
    List<Baja> bajas
) {

    /** El rol del usuario: el celular lo usa para mostrar solo lo que puede hacer. */
    public record Establecimiento(UUID id, String nombre, String localidad, String provincia, Rol rol) {
    }

    public record Cambios(
        List<Agrupacion> lotes,
        List<Agrupacion> rodeos,
        List<Producto> productos,
        List<Animal> animales,
        List<Evento> eventos
    ) {
    }

    /** Lote o rodeo. {@code activo: false} = dado de baja. */
    public record Agrupacion(UUID id, String nombre, String descripcion, boolean activo, Instant updatedAt) {
    }

    public record Producto(UUID id, String nombre, TipoProducto tipo, int diasCarencia, String dosisHabitual,
                           ViaAdministracion viaHabitual, boolean activo, Instant updatedAt) {
    }

    /** Todos los animales del establecimiento, también vendidos y muertos ({@code estado}). */
    public record Animal(UUID id, String caravana, String caravanaElectronica, Sexo sexo, Categoria categoria,
                         String raza, LocalDate fechaNacimiento, boolean fechaNacimientoEstimada, boolean castrado,
                         UUID loteId, UUID rodeoId, UUID madreId, UUID padreId, EstadoAnimal estado,
                         String observaciones, Instant updatedAt) {
    }

    /** {@code anulado: true} = el celular lo saca del historial. */
    public record Evento(UUID id, UUID operacionId, TipoEvento tipo, LocalDate fecha, UUID animalId, UUID loteId,
                         JsonNode datos, String registradoPor, Instant createdAt, boolean anulado,
                         Instant updatedAt) {
    }

    /** {@code entidad}: hoy solo ANIMAL (se borró o se trasladó a otro establecimiento). */
    public record Baja(String entidad, UUID id, Instant en) {
    }
}
