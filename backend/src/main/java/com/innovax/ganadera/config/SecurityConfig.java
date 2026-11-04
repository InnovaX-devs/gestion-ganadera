package com.innovax.ganadera.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.innovax.ganadera.admin.AdminAutenticado;
import com.innovax.ganadera.auth.TokenService;
import com.innovax.ganadera.cuenta.EstadoCuentas;

/**
 * API stateless: cada request se autentica con el access token ({@code Authorization: Bearer ...}).
 * Todo está protegido salvo los endpoints públicos.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/health",
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/activacion/*",
        "/api/admin/auth/login",
        // Avisos de Mercado Pago (#35): se validan con la firma y consultando a Mercado Pago
        "/api/mercadopago/webhook",
        "/error"
    };

    static final String ROL_USUARIO = "USUARIO";
    static final String ROL_ADMIN = "ADMIN";

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    private final EstadoCuentas estadoCuentas;

    public SecurityConfig(EstadoCuentas estadoCuentas) {
        this.estadoCuentas = estadoCuentas;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // Link de invitación: ver la invitación y registrarse con ella no requieren sesión.
                // Aceptarla estando logueado (POST /api/invitaciones/{token}/aceptar) sí.
                .requestMatchers(HttpMethod.GET, "/api/invitaciones/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/invitaciones/*/registro").permitAll()
                // Panel de Innovax: solo con la sesión del panel. Esa sesión no sirve para nada más.
                .requestMatchers("/api/admin/**").hasRole(ROL_ADMIN)
                .anyRequest().hasRole(ROL_USUARIO))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .build();
    }

    /**
     * Convierte el token validado en el usuario logueado ({@code AuthenticatedUser}, rol USUARIO). De
     * acá sale la organización que usa el filtro multi-tenant. El token del panel de administración da
     * un {@link AdminAutenticado} con rol ADMIN, sin organización.
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            if (TokenService.esAdmin(jwt)) {
                return new UsernamePasswordAuthenticationToken(new AdminAutenticado(jwt.getSubject()), jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + ROL_ADMIN)));
            }
            var usuario = TokenService.usuarioDesde(jwt);
            // Cuenta suspendida desde el panel (#35): el token deja de servir aunque no haya vencido
            if (estadoCuentas.suspendida(usuario.organizacionId())) {
                throw new DisabledException("Cuenta suspendida");
            }
            return new UsernamePasswordAuthenticationToken(usuario, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + ROL_USUARIO)));
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
