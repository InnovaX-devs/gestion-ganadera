package com.innovax.ganadera.calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.calendario.dto.PlanRequest;
import com.innovax.ganadera.calendario.dto.PlanResponse;
import com.innovax.ganadera.calendario.dto.RealizadoRequest;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.sanidad.ProductoSanitarioRepository;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Plan sanitario (#34): tratamientos planificados por lote, rodeo o establecimiento. Lo ve cualquier
 * miembro; lo arma quien puede registrar sanidad (dueño, encargado, veterinario).
 */
@Service
@RequiredArgsConstructor
public class PlanSanitarioService {

    /** Período máximo de una consulta. */
    static final int DIAS_MAXIMO = 400;

    private final PlanSanitarioRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;
    private final ProductoSanitarioRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacion;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public List<PlanResponse> listar(UUID establecimientoId, LocalDate desde, LocalDate hasta) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimiento(establecimientoId);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate inicio = desde != null ? desde : hoy.withDayOfMonth(1);
        LocalDate fin = hasta != null ? hasta : inicio.plusMonths(1).minusDays(1);
        if (inicio.isAfter(fin) || ChronoUnit.DAYS.between(inicio, fin) > DIAS_MAXIMO) {
            throw new DatosInvalidosException(List.of(new ApiError.FieldError("desde",
                "El período tiene que ir de desde a hasta, de hasta " + DIAS_MAXIMO + " días")));
        }
        return repository.delPeriodo(establecimientoId, inicio, fin).stream()
            .map(p -> PlanResponse.from(p, hoy)).toList();
    }

    /** Con {@code repeticiones} mayor a 1 se crea uno por cada repetición: devuelve todos. */
    @Transactional
    public List<PlanResponse> crear(UUID establecimientoId, PlanRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        Establecimiento establecimiento = establecimiento(establecimientoId);
        int veces = request.repeticiones() == null ? 1 : request.repeticiones();
        if (veces > 1 && request.cadaDias() == null) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("cadaDias", "Indicá cada cuántos días se repite")));
        }
        var creador = usuarioRepository.getReferenceById(TenantContext.requerirUsuario().userId());
        LocalDate hoy = LocalDate.now(reloj);

        List<PlanResponse> creados = new ArrayList<>();
        for (int i = 0; i < veces; i++) {
            TratamientoPlanificado plan = new TratamientoPlanificado(establecimiento, creador);
            aplicar(establecimientoId, request, plan);
            if (i > 0) plan.setFecha(request.fecha().plusDays((long) i * request.cadaDias()));
            creados.add(PlanResponse.from(repository.save(plan), hoy));
        }
        return creados;
    }

    @Transactional
    public PlanResponse actualizar(UUID establecimientoId, UUID id, PlanRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        TratamientoPlanificado plan = buscar(establecimientoId, id);
        if (!plan.esPendiente()) {
            throw new BusinessException("Solo se pueden editar los tratamientos pendientes");
        }
        aplicar(establecimientoId, request, plan);
        return PlanResponse.from(repository.saveAndFlush(plan), LocalDate.now(reloj));
    }

    @Transactional
    public PlanResponse marcarRealizado(UUID establecimientoId, UUID id, RealizadoRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        TratamientoPlanificado plan = buscar(establecimientoId, id);
        if (plan.getEstado() == EstadoPlan.CANCELADO) {
            throw new BusinessException("El tratamiento está cancelado");
        }
        plan.marcarRealizado(request == null ? null : request.operacionId(), reloj.instant());
        return PlanResponse.from(repository.saveAndFlush(plan), LocalDate.now(reloj));
    }

    /** Vuelve a pendiente uno marcado como hecho o cancelado. */
    @Transactional
    public PlanResponse reabrir(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        TratamientoPlanificado plan = buscar(establecimientoId, id);
        plan.reabrir();
        return PlanResponse.from(repository.saveAndFlush(plan), LocalDate.now(reloj));
    }

    @Transactional
    public PlanResponse cancelar(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        TratamientoPlanificado plan = buscar(establecimientoId, id);
        if (!plan.esPendiente()) {
            throw new BusinessException("Solo se pueden cancelar los tratamientos pendientes");
        }
        plan.cancelar();
        return PlanResponse.from(repository.saveAndFlush(plan), LocalDate.now(reloj));
    }

    @Transactional
    public void eliminar(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        repository.delete(buscar(establecimientoId, id));
    }

    private void aplicar(UUID establecimientoId, PlanRequest r, TratamientoPlanificado plan) {
        if (r.loteId() != null && r.rodeoId() != null) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("destino", "Elegí un lote o un rodeo, no los dos")));
        }
        plan.setFecha(r.fecha());
        plan.setLote(r.loteId() == null ? null : loteRepository
            .findByIdAndEstablecimientoIdAndActivoTrue(r.loteId(), establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Lote", r.loteId())));
        plan.setRodeo(r.rodeoId() == null ? null : rodeoRepository
            .findByIdAndEstablecimientoIdAndActivoTrue(r.rodeoId(), establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Rodeo", r.rodeoId())));
        plan.setProducto(r.productoId() == null ? null : productoRepository
            .findByIdAndEstablecimientoIdAndActivoTrue(r.productoId(), establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Producto", r.productoId())));
        plan.setDescripcion(r.descripcion().trim());
        plan.setDosis(limpiar(r.dosis()));
        plan.setNotas(limpiar(r.notas()));
        plan.setAvisarDiasAntes(r.avisarDiasAntes() == null ? 1 : r.avisarDiasAntes());
    }

    private TratamientoPlanificado buscar(UUID establecimientoId, UUID id) {
        return repository.findByIdAndEstablecimientoId(id, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Tratamiento planificado", id));
    }

    private Establecimiento establecimiento(UUID establecimientoId) {
        return establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
