package com.innovax.ganadera.sanidad;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.sanidad.dto.ProductoSanitarioRequest;
import com.innovax.ganadera.sanidad.dto.ProductoSanitarioResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Catálogo de productos sanitarios (issue #20), anidado en el establecimiento. */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/productos-sanitarios")
@RequiredArgsConstructor
public class ProductoSanitarioController {

    private final ProductoSanitarioService service;

    /** Productos activos, por nombre. */
    @GetMapping
    public List<ProductoSanitarioResponse> listar(@PathVariable UUID establecimientoId) {
        return service.listar(establecimientoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoSanitarioResponse crear(@PathVariable UUID establecimientoId,
                                           @Valid @RequestBody ProductoSanitarioRequest request) {
        return service.crear(establecimientoId, request);
    }

    @PutMapping("/{id}")
    public ProductoSanitarioResponse actualizar(@PathVariable UUID establecimientoId, @PathVariable UUID id,
                                                @Valid @RequestBody ProductoSanitarioRequest request) {
        return service.actualizar(establecimientoId, id, request);
    }

    /** Baja lógica. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.eliminar(establecimientoId, id);
    }
}
