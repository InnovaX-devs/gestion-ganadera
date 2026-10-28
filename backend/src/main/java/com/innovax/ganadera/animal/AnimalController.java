package com.innovax.ganadera.animal;

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

import com.innovax.ganadera.animal.dto.AnimalRequest;
import com.innovax.ganadera.animal.dto.AnimalResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Filtros, búsqueda y paginación del listado: issue #14. */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/animales")
@RequiredArgsConstructor
public class AnimalController {

    private final AnimalService service;

    @GetMapping
    public List<AnimalResponse> listar(@PathVariable UUID establecimientoId) {
        return service.listar(establecimientoId);
    }

    @GetMapping("/{id}")
    public AnimalResponse obtener(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.obtener(establecimientoId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnimalResponse crear(@PathVariable UUID establecimientoId, @Valid @RequestBody AnimalRequest request) {
        return service.crear(establecimientoId, request);
    }

    @PutMapping("/{id}")
    public AnimalResponse actualizar(@PathVariable UUID establecimientoId, @PathVariable UUID id,
                                     @Valid @RequestBody AnimalRequest request) {
        return service.actualizar(establecimientoId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.eliminar(establecimientoId, id);
    }
}
