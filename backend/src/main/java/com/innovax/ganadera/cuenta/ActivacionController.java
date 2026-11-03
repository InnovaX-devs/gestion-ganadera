package com.innovax.ganadera.cuenta;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.cuenta.dto.ActivacionResponse;
import com.innovax.ganadera.cuenta.dto.ActivarRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Link del email de activación: público (ver SecurityConfig). Al activar queda logueado. */
@RestController
@RequestMapping("/api/activacion/{token}")
@RequiredArgsConstructor
public class ActivacionController {

    private final CuentaService service;

    @GetMapping
    public ActivacionResponse ver(@PathVariable String token) {
        return service.ver(token);
    }

    @PostMapping
    public AuthResponse activar(@PathVariable String token, @Valid @RequestBody ActivarRequest request) {
        return service.activar(token, request.password());
    }
}
