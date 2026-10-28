package com.innovax.ganadera.membresia;

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

import com.innovax.ganadera.membresia.dto.MiembroRequest;
import com.innovax.ganadera.membresia.dto.MiembroResponse;
import com.innovax.ganadera.membresia.dto.MiembroRolRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/miembros")
@RequiredArgsConstructor
public class MembresiaController {

    private final MembresiaService service;

    @GetMapping
    public List<MiembroResponse> listar(@PathVariable UUID establecimientoId) {
        return service.listar(establecimientoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MiembroResponse agregar(@PathVariable UUID establecimientoId, @Valid @RequestBody MiembroRequest request) {
        return service.agregar(establecimientoId, request);
    }

    @PutMapping("/{id}")
    public MiembroResponse cambiarRol(@PathVariable UUID establecimientoId, @PathVariable UUID id,
                                      @Valid @RequestBody MiembroRolRequest request) {
        return service.cambiarRol(establecimientoId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.quitar(establecimientoId, id);
    }
}
