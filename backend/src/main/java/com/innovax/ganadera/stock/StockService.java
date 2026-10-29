package com.innovax.ganadera.stock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.agrupacion.Agrupacion;
import com.innovax.ganadera.agrupacion.AgrupacionRepository;
import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.stock.dto.StockGrupoResponse;
import com.innovax.ganadera.stock.dto.StockResponse;

import lombok.RequiredArgsConstructor;

/**
 * Stock de un establecimiento (#25). No hay una tabla de stock: siempre se calcula a partir de los
 * animales en estado {@link EstadoAnimal#ACTIVO}, así que no se puede desincronizar ni editar a mano.
 * Lo ve cualquier miembro del establecimiento.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;
    private final AutorizacionService autorizacion;

    @Transactional(readOnly = true)
    public StockResponse calcular(UUID establecimientoId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));

        Conteo general = new Conteo();
        // Clave null = sin lote / sin rodeo (HashMap acepta null como clave)
        Map<UUID, Conteo> porLote = new HashMap<>();
        Map<UUID, Conteo> porRodeo = new HashMap<>();
        for (StockFila fila : repository.contar(establecimientoId, EstadoAnimal.ACTIVO)) {
            general.sumar(fila);
            porLote.computeIfAbsent(fila.loteId(), id -> new Conteo()).sumar(fila);
            porRodeo.computeIfAbsent(fila.rodeoId(), id -> new Conteo()).sumar(fila);
        }

        return new StockResponse(
            establecimientoId,
            general.total(),
            general.porSexo(),
            general.porCategoria(),
            grupos(loteRepository, establecimientoId, porLote),
            grupos(rodeoRepository, establecimientoId, porRodeo));
    }

    /**
     * Todos los lotes (o rodeos) activos con su conteo, también los vacíos, ordenados por nombre. Al
     * final, el grupo de los que no tienen asignado, si hay alguno.
     */
    private static <E extends Agrupacion> List<StockGrupoResponse> grupos(
            AgrupacionRepository<E> agrupaciones, UUID establecimientoId, Map<UUID, Conteo> conteos) {
        Map<UUID, Conteo> pendientes = new HashMap<>(conteos);
        Conteo sinAsignar = pendientes.remove(null);
        List<StockGrupoResponse> resultado = new ArrayList<>();

        for (E agrupacion : agrupaciones.findByEstablecimientoIdAndActivoTrueOrderByNombreAsc(establecimientoId)) {
            Conteo conteo = pendientes.remove(agrupacion.getId());
            resultado.add((conteo == null ? new Conteo() : conteo).grupo(agrupacion.getId(), agrupacion.getNombre()));
        }

        // Animales activos en un lote o rodeo dado de baja. No debería pasar (no se puede dar de baja
        // uno con animales), pero si pasa se muestran igual para que los totales sigan cerrando.
        if (!pendientes.isEmpty()) {
            agrupaciones.findAllById(pendientes.keySet()).stream()
                .sorted(Comparator.comparing(Agrupacion::getNombre, String.CASE_INSENSITIVE_ORDER))
                .forEach(a -> resultado.add(pendientes.get(a.getId()).grupo(a.getId(), a.getNombre())));
        }

        if (sinAsignar != null) {
            resultado.add(sinAsignar.grupo(null, null));
        }
        return resultado;
    }
}
