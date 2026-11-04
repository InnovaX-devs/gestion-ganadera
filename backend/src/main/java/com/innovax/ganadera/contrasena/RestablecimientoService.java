package com.innovax.ganadera.contrasena;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.auth.AuthService;
import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.CuentaSuspendidaException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.contrasena.dto.RestablecimientoResponse;
import com.innovax.ganadera.cuenta.CuentaService;
import com.innovax.ganadera.invitacion.TokensInvitacion;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * "Olvidé mi contraseña": un link por email para elegir una nueva. Lo pide el usuario desde el login
 * o el equipo de Innovax desde el panel; en los dos casos la contraseña la elige el usuario.
 */
@Slf4j
@Service
public class RestablecimientoService {

    /** Entre dos pedidos para el mismo usuario, para que no se pueda llenar su casilla de emails. */
    static final Duration ESPERA_ENTRE_PEDIDOS = Duration.ofMinutes(1);

    private final RestablecimientoContrasenaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final CuentaService cuentaService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final Duration ttl;

    public RestablecimientoService(RestablecimientoContrasenaRepository repository, UsuarioRepository usuarioRepository,
                                   CuentaService cuentaService, AuthService authService,
                                   PasswordEncoder passwordEncoder, ApplicationEventPublisher eventos, Clock reloj,
                                   @Value("${app.contrasena.ttl:1h}") Duration ttl) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.cuentaService = cuentaService;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.reloj = reloj;
        this.ttl = ttl;
    }

    /**
     * Pedido desde el login. Nunca dice si el email existe: la respuesta es siempre la misma, así no
     * se puede averiguar quién tiene cuenta.
     */
    @Transactional
    public void pedir(String email) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (usuario == null || !usuario.isActivo() || usuario.getOrganizacion().isSuspendida()) {
            return;
        }
        if (pedidoReciente(usuario)) {
            return;
        }
        enviar(usuario, false);
    }

    /** Pedido desde el panel de Innovax, para el dueño de la cuenta. Devuelve a qué email se mandó. */
    @Transactional
    public String enviarAlDueno(UUID organizacionId) {
        Usuario dueno = usuarioRepository.findFirstByOrganizacionIdOrderByCreatedAtAsc(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        if (cuentaService.pendienteDeActivacion(dueno.getId())) {
            throw new BusinessException(
                "El dueño todavía no eligió su contraseña: usá \"Reenviar enlace\" para mandarle el de activación");
        }
        if (dueno.getOrganizacion().isSuspendida()) {
            throw new BusinessException("La cuenta está suspendida: reactivala antes de mandar el enlace");
        }
        if (pedidoReciente(dueno)) {
            throw new BusinessException("Ya se mandó un enlace hace menos de un minuto. Esperá y probá de nuevo.");
        }
        enviar(dueno, true);
        return dueno.getEmail();
    }

    // ---------- Lado del usuario (público, por el token) ----------

    @Transactional(readOnly = true)
    public RestablecimientoResponse ver(String token) {
        Usuario u = valido(token).getUsuario();
        return new RestablecimientoResponse(u.getEmail(), u.getNombre());
    }

    /** Cambia la contraseña, cierra las demás sesiones y lo deja logueado. */
    @Transactional
    public AuthResponse restablecer(String token, String password) {
        RestablecimientoContrasena pedido = valido(token);
        Usuario usuario = pedido.getUsuario();
        if (usuario.getOrganizacion().isSuspendida()) {
            throw new CuentaSuspendidaException();
        }
        Instant ahora = reloj.instant();
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setCredencialesCambiadasAt(ahora);
        pedido.usar(ahora);
        // Si nunca había activado la cuenta, elegir la contraseña por acá también la activa
        cuentaService.cerrarActivacionesPendientes(usuario.getId(), ahora);
        return authService.sesionPara(usuario);
    }

    private RestablecimientoContrasena valido(String token) {
        RestablecimientoContrasena pedido = repository.findByTokenHash(TokensInvitacion.hash(token))
            .orElseThrow(() -> new NotFoundException("El enlace no es válido. Revisá que esté completo."));
        String motivo = pedido.motivoInvalido(reloj.instant());
        if (motivo != null) throw new BusinessException(motivo);
        if (!pedido.getUsuario().isActivo()) {
            throw new BusinessException("Tu usuario está dado de baja. Pedile al dueño de la cuenta que te vuelva a invitar.");
        }
        return pedido;
    }

    private boolean pedidoReciente(Usuario usuario) {
        Instant limite = reloj.instant().minus(ESPERA_ENTRE_PEDIDOS);
        return repository.findFirstByUsuarioIdOrderByCreatedAtDesc(usuario.getId())
            .map(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(limite))
            .orElse(false);
    }

    private void enviar(Usuario usuario, boolean pedidoPorInnovax) {
        // Solo vale el último link
        repository.deleteAll(repository.findByUsuarioIdAndUsadoAtIsNull(usuario.getId()));
        String token = TokensInvitacion.generar();
        Instant vence = reloj.instant().plus(ttl);
        repository.save(new RestablecimientoContrasena(usuario, TokensInvitacion.hash(token), vence));
        eventos.publishEvent(new RestablecimientoEnviadoEvent(usuario.getEmail(), usuario.getNombre(), token, vence,
            pedidoPorInnovax));
    }
}
