package com.innovax.ganadera.establecimiento;

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

import com.innovax.ganadera.establecimiento.dto.EstablecimientoRequest;
import com.innovax.ganadera.establecimiento.dto.EstablecimientoResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/establecimientos")
@RequiredArgsConstructor
public class EstablecimientoController {

    private final EstablecimientoService service;

    @GetMapping
    public List<EstablecimientoResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public EstablecimientoResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstablecimientoResponse crear(@Valid @RequestBody EstablecimientoRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public EstablecimientoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody EstablecimientoRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        service.eliminar(id);
    }
}
