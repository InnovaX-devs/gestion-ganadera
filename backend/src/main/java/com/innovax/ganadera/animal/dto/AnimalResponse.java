package com.innovax.ganadera.animal.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.agrupacion.Agrupacion;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.sanidad.dto.CarenciaResponse;

public record AnimalResponse(
    UUID id,
    UUID establecimientoId,
    String caravana,
    String caravanaElectronica,
    Sexo sexo,
    Categoria categoria,
    String raza,
    LocalDate fechaNacimiento,
    boolean fechaNacimientoEstimada,
    boolean castrado,
    EstadoAnimal estado,
    Referencia lote,
    Referencia rodeo,
    Referencia madre,
    Referencia padre,
    String observaciones,
    Instant createdAt,
    /** Solo si está en período de carencia por algún tratamiento (#20); si no, null. */
    CarenciaResponse carencia
) {

    /** Dato relacionado con lo justo para mostrarlo: id y nombre (o caravana, si es un animal). */
    public record Referencia(UUID id, String nombre) {

        static Referencia de(Agrupacion a) {
            return a == null ? null : new Referencia(a.getId(), a.getNombre());
        }

        static Referencia de(Animal a) {
            return a == null ? null : new Referencia(a.getId(), a.getCaravana());
        }
    }

    /** Requiere una transacción abierta: lee lote, rodeo, madre y padre (relaciones lazy). */
    public static AnimalResponse from(Animal a) {
        return from(a, null);
    }

    public static AnimalResponse from(Animal a, CarenciaResponse carencia) {
        return new AnimalResponse(
            a.getId(),
            a.getEstablecimiento().getId(),
            a.getCaravana(),
            a.getCaravanaElectronica(),
            a.getSexo(),
            a.getCategoria(),
            a.getRaza(),
            a.getFechaNacimiento(),
            a.isFechaNacimientoEstimada(),
            a.isCastrado(),
            a.getEstado(),
            Referencia.de(a.getLote()),
            Referencia.de(a.getRodeo()),
            Referencia.de(a.getMadre()),
            Referencia.de(a.getPadre()),
            a.getObservaciones(),
            a.getCreatedAt(),
            carencia);
    }
}
