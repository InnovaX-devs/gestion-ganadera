package com.innovax.ganadera.cobro;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Rutas en las que una cuenta sin uso pago puede seguir escribiendo (#35): entrar, pagar y
 * sincronizar lo cargado en el campo sin señal (eso no se pierde nunca).
 */
@Configuration
@RequiredArgsConstructor
public class CobroWebConfig implements WebMvcConfigurer {

    private final PagoRequeridoInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/**",
                "/api/plan",
                "/api/suscripcion",
                "/api/suscripcion/**",
                "/api/admin/**",
                "/api/mercadopago/**",
                "/api/activacion/**",
                "/api/invitaciones/**",
                "/api/health",
                // Lo cargado sin señal antes del bloqueo se guarda igual (docs/sync.md)
                "/api/sync",
                "/api/sync/**");
    }
}
