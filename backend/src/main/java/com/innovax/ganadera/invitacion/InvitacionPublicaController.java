package com.innovax.ganadera.invitacion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.invitacion.dto.AceptacionResponse;
import com.innovax.ganadera.invitacion.dto.InvitacionPublicaResponse;
import com.innovax.ganadera.invitacion.dto.RegistroInvitadoRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Lado del invitado, identificado por el token del link. {@code GET} y {@code /registro} son
 * públicos (ver SecurityConfig); {@code /aceptar} requiere estar logueado.
 */
@RestController
@RequestMapping("/api/invitaciones/{token}")
@RequiredArgsConstructor
public class InvitacionPublicaController {

    private final InvitacionAceptacionService service;

    @GetMapping
    public InvitacionPublicaResponse ver(@PathVariable String token) {
        return service.ver(token);
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registrar(@PathVariable String token, @Valid @RequestBody RegistroInvitadoRequest request) {
        return service.registrar(token, request);
    }

    @PostMapping("/aceptar")
    public AceptacionResponse aceptar(@PathVariable String token) {
        return service.aceptar(token);
    }
}
