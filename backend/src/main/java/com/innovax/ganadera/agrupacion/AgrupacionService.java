package com.innovax.ganadera.agrupacion;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.agrupacion.dto.AgrupacionRequest;
import com.innovax.ganadera.agrupacion.dto.AgrupacionResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;

/**
 * Lógica común del ABM de lotes y rodeos. Todo cuelga de un establecimiento: si el establecimiento
 * no existe, está dado de baja o es de otra organización, responde 404.
 */
public abstract class AgrupacionService<E extends Agrupacion> {

    private final AgrupacionRepository<E> repository;
    private final EstablecimientoRepository establecimientoRepository;

    protected AgrupacionService(AgrupacionRepository<E> repository,
                                EstablecimientoRepository establecimientoRepository) {
        this.repository = repository;
        this.establecimientoRepository = establecimientoRepository;
    }

    /** Ej: "lote". Se usa en los mensajes de error. */
    protected abstract String nombreRecurso();

    protected abstract E nueva(Establecimiento establecimiento, String nombre);

    @Transactional(readOnly = true)
    public List<AgrupacionResponse> listar(UUID establecimientoId) {
        validarEstablecimiento(establecimientoId);
        return repository.findByEstablecimientoIdAndActivoTrueOrderByNombreAsc(establecimientoId).stream()
            .map(this::aRespuesta)
            .toList();
    }

    @Transactional(readOnly = true)
    public AgrupacionResponse obtener(UUID establecimientoId, UUID id) {
        return aRespuesta(buscar(establecimientoId, id));
    }

    @Transactional
    public AgrupacionResponse crear(UUID establecimientoId, AgrupacionRequest request) {
        Establecimiento establecimiento = validarEstablecimiento(establecimientoId);
        if (repository.existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrue(establecimientoId, request.nombre())) {
            throw nombreRepetido(request.nombre());
        }
        E agrupacion = nueva(establecimiento, request.nombre());
        agrupacion.setDescripcion(request.descripcion());
        return aRespuesta(repository.save(agrupacion));
    }

    @Transactional
    public AgrupacionResponse actualizar(UUID establecimientoId, UUID id, AgrupacionRequest request) {
        E agrupacion = buscar(establecimientoId, id);
        if (repository.existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(
                establecimientoId, request.nombre(), id)) {
            throw nombreRepetido(request.nombre());
        }
        agrupacion.setNombre(request.nombre());
        agrupacion.setDescripcion(request.descripcion());
        return aRespuesta(repository.saveAndFlush(agrupacion));
    }

    /** Baja lógica. No se puede si tiene animales asignados. */
    @Transactional
    public void eliminar(UUID establecimientoId, UUID id) {
        E agrupacion = buscar(establecimientoId, id);
        long animales = contarAnimales(agrupacion);
        if (animales > 0) {
            throw new BusinessException("No se puede eliminar: tiene " + animales
                + (animales == 1 ? " animal asignado" : " animales asignados")
                + ". Movelos a otro " + nombreRecurso() + " primero.");
        }
        agrupacion.darDeBaja();
    }

    /** Se completa cuando exista el modelo Animal (#13). */
    protected long contarAnimales(E agrupacion) {
        return 0;
    }

    private AgrupacionResponse aRespuesta(E agrupacion) {
        return AgrupacionResponse.from(agrupacion, contarAnimales(agrupacion));
    }

    private Establecimiento validarEstablecimiento(UUID establecimientoId) {
        return establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));
    }

    private E buscar(UUID establecimientoId, UUID id) {
        validarEstablecimiento(establecimientoId);
        return repository.findByIdAndEstablecimientoIdAndActivoTrue(id, establecimientoId)
            .orElseThrow(() -> NotFoundException.of(capitalizar(nombreRecurso()), id));
    }

    private ConflictException nombreRepetido(String nombre) {
        return new ConflictException("Ya hay un " + nombreRecurso() + " llamado \"" + nombre
            + "\" en este establecimiento");
    }

    private static String capitalizar(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
