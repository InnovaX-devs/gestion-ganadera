package com.innovax.ganadera.agrupacion;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.innovax.ganadera.agrupacion.dto.AgrupacionRequest;
import com.innovax.ganadera.agrupacion.dto.AgrupacionResponse;

import jakarta.validation.Valid;

/**
 * Endpoints comunes de lotes y rodeos, anidados en el establecimiento:
 * {@code /api/establecimientos/{establecimientoId}/lotes} y {@code .../rodeos}.
 */
public abstract class AgrupacionController {

    private final AgrupacionService<?> service;

    protected AgrupacionController(AgrupacionService<?> service) {
        this.service = service;
    }

    @GetMapping
    public List<AgrupacionResponse> listar(@PathVariable UUID establecimientoId) {
        return service.listar(establecimientoId);
    }

    @GetMapping("/{id}")
    public AgrupacionResponse obtener(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.obtener(establecimientoId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgrupacionResponse crear(@PathVariable UUID establecimientoId,
                                    @Valid @RequestBody AgrupacionRequest request) {
        return service.crear(establecimientoId, request);
    }

    @PutMapping("/{id}")
    public AgrupacionResponse actualizar(@PathVariable UUID establecimientoId, @PathVariable UUID id,
                                         @Valid @RequestBody AgrupacionRequest request) {
        return service.actualizar(establecimientoId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.eliminar(establecimientoId, id);
    }
}
