package com.innovax.ganadera.sync;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.innovax.ganadera.animal.Animal;

import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreRemove;

/**
 * Anota en {@code sync_baja} cuando un animal sale de un establecimiento (#30): si se borra, o si
 * se traslada a otro campo (un movimiento, o la anulación de uno). Es un listener de JPA para que
 * ningún camino se lo saltee: hoy hay tres (borrar el animal, anular su nacimiento y los movimientos).
 *
 * <p>Escribe con JDBC en la misma transacción: durante el flush no se pueden guardar entidades
 * nuevas con JPA. Spring crea el listener e inyecta sus dependencias.
 */
public class BajaSyncListener {

    static final String ANIMAL = "ANIMAL";

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public BajaSyncListener(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @PreRemove
    void alBorrar(Animal animal) {
        registrar(animal.getOrganizacionId(), animal.getEstablecimiento().getId(), animal.getId());
    }

    @PostUpdate
    void alActualizar(Animal animal) {
        UUID anterior = animal.consumirEstablecimientoAnterior();
        if (anterior != null && !anterior.equals(animal.getEstablecimiento().getId())) {
            registrar(animal.getOrganizacionId(), anterior, animal.getId());
        }
    }

    private void registrar(UUID organizacionId, UUID establecimientoId, UUID animalId) {
        Timestamp ahora = Timestamp.from(reloj.instant());
        jdbc.update("""
            INSERT INTO sync_baja (id, organizacion_id, establecimiento_id, entidad, entidad_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), organizacionId, establecimientoId, ANIMAL, animalId, ahora, ahora);
    }
}
