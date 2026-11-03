package com.innovax.ganadera.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Service;

import com.innovax.ganadera.admin.dto.AdminLoginRequest;
import com.innovax.ganadera.admin.dto.AdminSesionResponse;
import com.innovax.ganadera.auth.TokenService;
import com.innovax.ganadera.common.error.DemasiadosIntentosException;
import com.innovax.ganadera.common.error.UnauthorizedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Login del panel de administración: email de ADMIN_EMAILS + ADMIN_PASSWORD. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    static final String CREDENCIALES_INVALIDAS = "Email o contraseña incorrectos";

    private final AdminProperties properties;
    private final Administradores administradores;
    private final IntentosLoginAdmin intentos;
    private final TokenService tokenService;

    public AdminSesionResponse login(AdminLoginRequest request) {
        if (intentos.bloqueado()) {
            throw new DemasiadosIntentosException(
                "Demasiados intentos fallidos. Esperá 15 minutos y probá de nuevo.");
        }
        String email = request.email().trim().toLowerCase();
        // Se compara siempre la contraseña (aunque el email no sea de la lista) y en tiempo constante
        boolean passwordOk = !properties.password().isEmpty() && MessageDigest.isEqual(
            properties.password().getBytes(StandardCharsets.UTF_8), request.password().getBytes(StandardCharsets.UTF_8));
        if (!passwordOk || !administradores.esAdministrador(email)) {
            intentos.fallo();
            log.warn("Administración: intento de ingreso fallido con el email {}", email);
            throw new UnauthorizedException(CREDENCIALES_INVALIDAS);
        }
        intentos.reiniciar();
        log.info("Administración: ingresó {}", email);
        return new AdminSesionResponse(tokenService.generarAdmin(email, properties.sesion()), email,
            properties.sesion().toSeconds());
    }

    public AdminSesionResponse.Admin actual() {
        return new AdminSesionResponse.Admin(administradores.exigir());
    }
}
