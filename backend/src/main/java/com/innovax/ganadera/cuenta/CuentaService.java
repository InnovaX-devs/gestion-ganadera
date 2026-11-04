package com.innovax.ganadera.cuenta;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.auth.AuthService;
import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.CuentaSuspendidaException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.cuenta.dto.ActivacionResponse;
import com.innovax.ganadera.cuenta.dto.CrearCuentaRequest;
import com.innovax.ganadera.invitacion.InvitacionProperties;
import com.innovax.ganadera.invitacion.TokensInvitacion;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;
import com.innovax.ganadera.plan.PlanService;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cuentas manejadas por el equipo de Innovax (#35): crearlas con un link de activación para el
 * dueño, y suspenderlas o reactivarlas. Quien llama (el panel de administración) controla que sea
 * un administrador; la activación es pública, por el token del link.
 */
@Service
@RequiredArgsConstructor
public class CuentaService {

    private final OrganizacionRepository organizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ActivacionCuentaRepository activacionRepository;
    private final PlanService planService;
    private final AuthService authService;
    private final EstadoCuentas estadoCuentas;
    private final PasswordEncoder passwordEncoder;
    private final InvitacionProperties invitaciones;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    /** Crea la organización y su dueño (sin contraseña) y le manda el link. Devuelve el id de la organización. */
    @Transactional
    public UUID crear(CrearCuentaRequest request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Ya existe una cuenta con ese email");
        }
        Organizacion organizacion = organizacionRepository.save(new Organizacion(request.nombreOrganizacion()));
        planService.asignar(organizacion.getId(), request.planCodigo(), request.pruebaHasta());
        // Contraseña imposible de adivinar: hasta que active la cuenta no puede entrar
        Usuario dueno = usuarioRepository.save(new Usuario(organizacion, request.email(),
            passwordEncoder.encode(UUID.randomUUID().toString()), request.nombre(), request.apellido()));
        enviarActivacion(dueno);
        return organizacion.getId();
    }

    /** Link nuevo para el dueño (el anterior deja de funcionar). Solo si todavía no activó la cuenta. */
    @Transactional
    public void reenviarActivacion(UUID organizacionId) {
        Usuario dueno = usuarioRepository.findFirstByOrganizacionIdOrderByCreatedAtAsc(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        var pendientes = activacionRepository.findByUsuarioIdAndUsadaAtIsNull(dueno.getId());
        if (pendientes.isEmpty()) {
            throw new BusinessException("El dueño ya eligió su contraseña: no hace falta reenviar el enlace");
        }
        activacionRepository.deleteAll(pendientes);
        enviarActivacion(dueno);
    }

    /** Si el dueño todavía no eligió su contraseña. */
    @Transactional(readOnly = true)
    public boolean pendienteDeActivacion(UUID usuarioId) {
        return activacionRepository.existsByUsuarioIdAndUsadaAtIsNull(usuarioId);
    }

    /** El usuario ya eligió su contraseña por otro camino (ej: "olvidé mi contraseña"): los links de activación no sirven más. */
    @Transactional
    public void cerrarActivacionesPendientes(UUID usuarioId, Instant ahora) {
        activacionRepository.findByUsuarioIdAndUsadaAtIsNull(usuarioId).forEach(a -> a.usar(ahora));
    }

    @Transactional
    public void suspender(UUID organizacionId, boolean suspendida) {
        Organizacion organizacion = organizacionRepository.findById(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        organizacion.setSuspendida(suspendida);
        estadoCuentas.olvidar(organizacionId);
    }

    // ---------- Lado del dueño (público, por el token) ----------

    @Transactional(readOnly = true)
    public ActivacionResponse ver(String token) {
        ActivacionCuenta activacion = valida(token);
        Usuario u = activacion.getUsuario();
        return new ActivacionResponse(u.getEmail(), u.getNombre(), u.getOrganizacion().getNombre());
    }

    @Transactional
    public AuthResponse activar(String token, String password) {
        ActivacionCuenta activacion = valida(token);
        Usuario usuario = activacion.getUsuario();
        if (usuario.getOrganizacion().isSuspendida()) {
            throw new CuentaSuspendidaException();
        }
        usuario.setPasswordHash(passwordEncoder.encode(password));
        activacion.usar(reloj.instant());
        return authService.sesionPara(usuario);
    }

    private ActivacionCuenta valida(String token) {
        ActivacionCuenta activacion = activacionRepository.findByTokenHash(TokensInvitacion.hash(token))
            .orElseThrow(() -> new NotFoundException("El enlace no es válido. Revisá que esté completo."));
        String motivo = activacion.motivoInvalida(reloj.instant());
        if (motivo != null) throw new BusinessException(motivo);
        return activacion;
    }

    private void enviarActivacion(Usuario dueno) {
        String token = TokensInvitacion.generar();
        Instant vence = reloj.instant().plus(invitaciones.ttl());
        activacionRepository.save(new ActivacionCuenta(dueno, TokensInvitacion.hash(token), vence));
        eventos.publishEvent(new ActivacionEnviadaEvent(dueno.getEmail(), dueno.getNombre(),
            dueno.getOrganizacion().getNombre(), token, vence));
    }
}
