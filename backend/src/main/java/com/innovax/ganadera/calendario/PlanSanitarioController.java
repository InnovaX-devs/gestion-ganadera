package com.innovax.ganadera.calendario;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.calendario.dto.AlertasResponse;
import com.innovax.ganadera.calendario.dto.PlanRequest;
import com.innovax.ganadera.calendario.dto.PlanResponse;
import com.innovax.ganadera.calendario.dto.RealizadoRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Calendario sanitario (#34): tratamientos planificados y alertas del establecimiento. */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}")
@RequiredArgsConstructor
public class PlanSanitarioController {

    private final PlanSanitarioService service;
    private final AlertaService alertaService;

    /** Por defecto, el mes en curso. */
    @GetMapping("/plan-sanitario")
    public List<PlanResponse> listar(
            @PathVariable UUID establecimientoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listar(establecimientoId, desde, hasta);
    }

    /** Devuelve todos los creados (más de uno si se repite). */
    @PostMapping("/plan-sanitario")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PlanResponse> crear(@PathVariable UUID establecimientoId, @Valid @RequestBody PlanRequest request) {
        return service.crear(establecimientoId, request);
    }

    @PutMapping("/plan-sanitario/{id}")
    public PlanResponse actualizar(@PathVariable UUID establecimientoId, @PathVariable UUID id,
                                   @Valid @RequestBody PlanRequest request) {
        return service.actualizar(establecimientoId, id, request);
    }

    @PostMapping("/plan-sanitario/{id}/realizado")
    public PlanResponse marcarRealizado(@PathVariable UUID establecimientoId, @PathVariable UUID id,
                                        @RequestBody(required = false) RealizadoRequest request) {
        return service.marcarRealizado(establecimientoId, id, request);
    }

    @PostMapping("/plan-sanitario/{id}/reabrir")
    public PlanResponse reabrir(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.reabrir(establecimientoId, id);
    }

    @PostMapping("/plan-sanitario/{id}/cancelar")
    public PlanResponse cancelar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.cancelar(establecimientoId, id);
    }

    @DeleteMapping("/plan-sanitario/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.eliminar(establecimientoId, id);
    }

    @GetMapping("/alertas")
    public AlertasResponse alertas(@PathVariable UUID establecimientoId) {
        return alertaService.alertas(establecimientoId);
    }
}
