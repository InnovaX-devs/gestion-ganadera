package com.innovax.ganadera.establecimiento;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.dto.EstablecimientoRequest;
import com.innovax.ganadera.establecimiento.dto.EstablecimientoResponse;

import lombok.RequiredArgsConstructor;

/**
 * ABM de establecimientos. No hay que filtrar por organización: lo hace Hibernate (@TenantId).
 * Un establecimiento de otra organización simplemente "no existe" (404).
 *
 * <p>Permisos por rol: se agregan en el issue de membresías y roles (#11).
 */
@Service
@RequiredArgsConstructor
public class EstablecimientoService {

    private final EstablecimientoRepository repository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;

    @Transactional(readOnly = true)
    public List<EstablecimientoResponse> listar() {
        return repository.findByActivoTrueOrderByNombreAsc().stream()
            .map(EstablecimientoResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public EstablecimientoResponse obtener(UUID id) {
        return EstablecimientoResponse.from(buscar(id));
    }

    @Transactional
    public EstablecimientoResponse crear(EstablecimientoRequest request) {
        if (repository.existsByNombreIgnoreCaseAndActivoTrue(request.nombre())) {
            throw nombreRepetido(request.nombre());
        }
        Establecimiento establecimiento = new Establecimiento(request.nombre());
        copiarDatos(request, establecimiento);
        return EstablecimientoResponse.from(repository.save(establecimiento));
    }

    @Transactional
    public EstablecimientoResponse actualizar(UUID id, EstablecimientoRequest request) {
        Establecimiento establecimiento = buscar(id);
        if (repository.existsByNombreIgnoreCaseAndActivoTrueAndIdNot(request.nombre(), id)) {
            throw nombreRepetido(request.nombre());
        }
        copiarDatos(request, establecimiento);
        return EstablecimientoResponse.from(repository.saveAndFlush(establecimiento));
    }

    /**
     * Baja lógica. Solo si no tiene lotes ni rodeos activos, para que no quede hacienda "perdida"
     * en un campo eliminado por error.
     */
    @Transactional
    public void eliminar(UUID id) {
        Establecimiento establecimiento = buscar(id);
        long lotes = loteRepository.countByEstablecimientoIdAndActivoTrue(id);
        long rodeos = rodeoRepository.countByEstablecimientoIdAndActivoTrue(id);
        if (lotes + rodeos > 0) {
            String detalle = java.util.stream.Stream.of(
                    cantidad(lotes, "lote", "lotes"), cantidad(rodeos, "rodeo", "rodeos"))
                .filter(t -> !t.isEmpty())
                .collect(java.util.stream.Collectors.joining(" y "));
            throw new BusinessException("No se puede eliminar: todavía tiene " + detalle + ". Eliminalos primero.");
        }
        establecimiento.darDeBaja();
    }

    private static String cantidad(long n, String singular, String plural) {
        return n == 0 ? "" : n + " " + (n == 1 ? singular : plural);
    }

    private Establecimiento buscar(UUID id) {
        return repository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    private static void copiarDatos(EstablecimientoRequest request, Establecimiento e) {
        e.setNombre(request.nombre());
        e.setLocalidad(request.localidad());
        e.setProvincia(request.provincia());
        e.setSuperficieHa(request.superficieHa());
        e.setRenspa(request.renspa());
        e.setLatitud(request.latitud());
        e.setLongitud(request.longitud());
    }

    private static ConflictException nombreRepetido(String nombre) {
        return new ConflictException("Ya tenés un establecimiento llamado \"" + nombre + "\"");
    }
}
