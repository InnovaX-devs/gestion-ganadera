package com.innovax.ganadera.evento;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.innovax.ganadera.evento.dto.EventoFiltro;

import jakarta.persistence.criteria.Predicate;

final class EventoSpecs {

    private EventoSpecs() {
    }

    /**
     * Eventos no anulados del establecimiento, con los filtros que vengan cargados.
     *
     * @param historialDelAnimal true cuando se pide el historial de un animal que está hoy en este
     *                           establecimiento: se incluye lo registrado en los otros campos por
     *                           donde pasó (#22), así su historia no se corta al moverlo.
     */
    static Specification<Evento> de(UUID establecimientoId, EventoFiltro filtro, boolean historialDelAnimal) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (!historialDelAnimal) {
                condiciones.add(cb.equal(root.get("establecimiento").get("id"), establecimientoId));
            }
            condiciones.add(cb.isNull(root.get("deletedAt")));
            if (filtro.tipo() != null) {
                condiciones.add(cb.equal(root.get("tipo"), filtro.tipo()));
            }
            if (filtro.animalId() != null) {
                condiciones.add(cb.equal(root.get("animal").get("id"), filtro.animalId()));
            }
            if (filtro.loteId() != null) {
                condiciones.add(cb.equal(root.get("lote").get("id"), filtro.loteId()));
            }
            if (filtro.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("fecha"), filtro.desde()));
            }
            if (filtro.hasta() != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.<LocalDate>get("fecha"), filtro.hasta()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
