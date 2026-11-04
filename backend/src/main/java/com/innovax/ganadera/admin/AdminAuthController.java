package com.innovax.ganadera.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.admin.dto.AdminLoginRequest;
import com.innovax.ganadera.admin.dto.AdminSesionResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ingreso al panel de administración, separado del login de los clientes. */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService service;

    @PostMapping("/login")
    public AdminSesionResponse login(@Valid @RequestBody AdminLoginRequest request) {
        return service.login(request);
    }

    /** Para que la web sepa si la sesión del panel sigue valiendo. */
    @GetMapping("/me")
    public AdminSesionResponse.Admin me() {
        return service.actual();
    }
}
