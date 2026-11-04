package com.innovax.ganadera.contrasena;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.contrasena.dto.NuevaContrasenaRequest;
import com.innovax.ganadera.contrasena.dto.OlvideContrasenaRequest;
import com.innovax.ganadera.contrasena.dto.RestablecimientoResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** "Olvidé mi contraseña": todo público (ver SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class RestablecimientoController {

    private final RestablecimientoService service;

    /** Siempre 204, exista o no el email. */
    @PostMapping("/api/auth/olvide-contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void olvide(@Valid @RequestBody OlvideContrasenaRequest request) {
        service.pedir(request.email());
    }

    @GetMapping("/api/restablecer/{token}")
    public RestablecimientoResponse ver(@PathVariable String token) {
        return service.ver(token);
    }

    @PostMapping("/api/restablecer/{token}")
    public AuthResponse restablecer(@PathVariable String token, @Valid @RequestBody NuevaContrasenaRequest request) {
        return service.restablecer(token, request.password());
    }
}
