package com.innovax.ganadera.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.auth.dto.LoginRequest;
import com.innovax.ganadera.auth.dto.RegisterRequest;
import com.innovax.ganadera.auth.dto.UsuarioResponse;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.CuentaSuspendidaException;
import com.innovax.ganadera.common.error.UnauthorizedException;
import com.innovax.ganadera.common.tenant.AuthenticatedUser;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;
import com.innovax.ganadera.plan.PlanService;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

@Service
public class AuthService {

    static final String CREDENCIALES_INVALIDAS = "Email o contraseña incorrectos";
    static final String SESION_VENCIDA = "La sesión venció, volvé a ingresar";

    private final UsuarioRepository usuarioRepository;
    private final OrganizacionRepository organizacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PlanService planService;

    /**
     * Hash de relleno: si el email no existe igual se compara una contraseña, para que la respuesta
     * tarde lo mismo y no se pueda averiguar qué emails están registrados midiendo tiempos.
     */
    private final String hashDeRelleno;

    public AuthService(UsuarioRepository usuarioRepository, OrganizacionRepository organizacionRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService, PlanService planService) {
        this.usuarioRepository = usuarioRepository;
        this.organizacionRepository = organizacionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.planService = planService;
        this.hashDeRelleno = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Crea la organización y su primer usuario (el dueño) y lo deja logueado. */
    @Transactional
    public AuthResponse registrar(RegisterRequest request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.email().trim())) {
            throw new ConflictException("Ya existe una cuenta con ese email");
        }
        Organizacion nueva = new Organizacion(request.nombreOrganizacion().trim());
        planService.iniciarPrueba(nueva);
        Organizacion organizacion = organizacionRepository.save(nueva);
        Usuario usuario = usuarioRepository.save(new Usuario(
            organizacion,
            request.email(),
            passwordEncoder.encode(request.password()),
            request.nombre().trim(),
            limpiar(request.apellido())));
        return respuestaPara(usuario);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String hash = usuario != null ? usuario.getPasswordHash() : hashDeRelleno;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);
        if (usuario == null || !passwordOk || !usuario.isActivo()) {
            throw new UnauthorizedException(CREDENCIALES_INVALIDAS);
        }
        // Recién con la contraseña correcta: si no, se podría averiguar qué cuentas están suspendidas
        if (usuario.getOrganizacion().isSuspendida()) {
            throw new CuentaSuspendidaException();
        }
        return respuestaPara(usuario);
    }

    @Transactional(readOnly = true)
    public AuthResponse refrescar(String refreshToken) {
        Jwt token;
        UUID usuarioId;
        try {
            token = tokenService.decodificarRefreshToken(refreshToken);
            usuarioId = UUID.fromString(token.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException(SESION_VENCIDA);
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new UnauthorizedException(SESION_VENCIDA));
        if (emitidoAntesDelCambioDeContrasena(token, usuario)) {
            throw new UnauthorizedException(SESION_VENCIDA);
        }
        if (usuario.getOrganizacion().isSuspendida()) {
            throw new CuentaSuspendidaException();
        }
        return respuestaPara(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse usuarioActual() {
        AuthenticatedUser actual = TenantContext.requerirUsuario();
        return usuarioRepository.findById(actual.userId())
            .filter(Usuario::isActivo)
            .map(UsuarioResponse::from)
            .orElseThrow(() -> new UnauthorizedException(SESION_VENCIDA));
    }

    /** Tokens y datos de sesión para un usuario ya creado (ej: quien se registra desde una invitación). */
    public AuthResponse sesionPara(Usuario usuario) {
        return respuestaPara(usuario);
    }

    private AuthResponse respuestaPara(Usuario usuario) {
        var autenticado = new AuthenticatedUser(usuario.getId(), usuario.getOrganizacion().getId(), usuario.getEmail());
        return AuthResponse.of(tokenService.generar(autenticado), UsuarioResponse.from(usuario));
    }

    /** Las sesiones abiertas antes de cambiar la contraseña se cierran (el JWT guarda la hora en segundos). */
    private static boolean emitidoAntesDelCambioDeContrasena(Jwt token, Usuario usuario) {
        Instant cambio = usuario.getCredencialesCambiadasAt();
        return cambio != null && token.getIssuedAt() != null
            && token.getIssuedAt().isBefore(cambio.truncatedTo(ChronoUnit.SECONDS));
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
