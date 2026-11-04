package com.innovax.ganadera.animal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.innovax.ganadera.animal.dto.AnimalFiltro;

import jakarta.persistence.criteria.Predicate;

/** Arma la consulta del listado a partir de los filtros. Solo agrega las condiciones que vienen cargadas. */
final class AnimalSpecs {

    private AnimalSpecs() {
    }

    static Specification<Animal> de(UUID establecimientoId, AnimalFiltro filtro) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(root.get("establecimiento").get("id"), establecimientoId));
            condiciones.add(cb.equal(root.get("estado"), filtro.estadoOPorDefecto()));

            if (filtro.q() != null && !filtro.q().isBlank()) {
                String patron = "%" + escaparLike(filtro.q().trim().toUpperCase(Locale.ROOT)) + "%";
                condiciones.add(cb.or(
                    cb.like(root.<String>get("caravana"), patron, '\\'),
                    cb.like(root.<String>get("caravanaElectronica"), patron, '\\')));
            }
            if (filtro.categoria() != null) {
                condiciones.add(cb.equal(root.get("categoria"), filtro.categoria()));
            }
            if (filtro.sexo() != null) {
                condiciones.add(cb.equal(root.get("sexo"), filtro.sexo()));
            }
            if (filtro.loteId() != null) {
                condiciones.add(cb.equal(root.get("lote").get("id"), filtro.loteId()));
            } else if (Boolean.TRUE.equals(filtro.sinLote())) {
                condiciones.add(cb.isNull(root.get("lote")));
            }
            if (filtro.rodeoId() != null) {
                condiciones.add(cb.equal(root.get("rodeo").get("id"), filtro.rodeoId()));
            } else if (Boolean.TRUE.equals(filtro.sinRodeo())) {
                condiciones.add(cb.isNull(root.get("rodeo")));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    /** Para que un "%" o "_" que escriba el usuario se busque literal y no como comodín. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
