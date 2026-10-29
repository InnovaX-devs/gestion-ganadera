package com.innovax.ganadera.invitacion;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.invitacion.dto.InvitacionRequest;
import com.innovax.ganadera.invitacion.dto.InvitacionResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Lado del dueño: gestiona las invitaciones de un establecimiento. */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/invitaciones")
@RequiredArgsConstructor
public class InvitacionController {

    private final InvitacionService service;

    @GetMapping
    public List<InvitacionResponse> listar(@PathVariable UUID establecimientoId) {
        return service.listar(establecimientoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitacionResponse invitar(@PathVariable UUID establecimientoId,
                                      @Valid @RequestBody InvitacionRequest request) {
        return service.invitar(establecimientoId, request);
    }

    @PostMapping("/{id}/reenviar")
    public InvitacionResponse reenviar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.reenviar(establecimientoId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.revocar(establecimientoId, id);
    }
}
