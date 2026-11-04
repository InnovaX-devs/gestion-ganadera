package com.innovax.ganadera.cobro;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.innovax.ganadera.common.tenant.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Cuenta sin uso pago: queda en <b>solo lectura</b> (#35). Puede ver todo (GET), pero lo que carga,
 * modifica o borra responde 402, salvo lo necesario para entrar y pagar y la sincronización de lo
 * cargado en el campo sin señal. Ver {@link CobroWebConfig} para las rutas libres y {@link EstadoPago}.
 */
@Component
@RequiredArgsConstructor
public class PagoRequeridoInterceptor implements HandlerInterceptor {

    private static final Set<String> LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    private final EstadoPago estadoPago;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (LECTURA.contains(request.getMethod())) return true;
        TenantContext.usuarioActual().ifPresent(usuario -> {
            if (!estadoPago.habilitada(usuario.organizacionId())) {
                throw new PagoRequeridoException();
            }
        });
        return true;
    }
}
