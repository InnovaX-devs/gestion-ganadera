package com.innovax.ganadera.calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.calendario.dto.AlertasResponse;
import com.innovax.ganadera.calendario.dto.PlanResponse;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.mail.EmailSender;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Membresia;
import com.innovax.ganadera.membresia.MembresiaRepository;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.sanidad.CarenciaService;
import com.innovax.ganadera.sanidad.dto.CarenciaResponse;
import com.innovax.ganadera.usuario.Usuario;

import lombok.extern.slf4j.Slf4j;

/**
 * Alertas sanitarias (#34): tratamientos planificados vencidos o próximos y animales que terminan su
 * carencia. Se ven en la web y llegan por email una vez por día (ver {@link AlertasJob}).
 */
@Slf4j
@Service
public class AlertaService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PlanSanitarioRepository planRepository;
    private final AlertaEnviadaRepository enviadaRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final MembresiaRepository membresiaRepository;
    private final CarenciaService carenciaService;
    private final AutorizacionService autorizacion;
    private final EmailSender email;
    private final Clock reloj;
    private final int diasProximos;
    private final String webUrl;

    public AlertaService(PlanSanitarioRepository planRepository, AlertaEnviadaRepository enviadaRepository,
                         EstablecimientoRepository establecimientoRepository, MembresiaRepository membresiaRepository,
                         CarenciaService carenciaService, AutorizacionService autorizacion, EmailSender email,
                         Clock reloj, @Value("${app.alertas.dias-proximos:7}") int diasProximos,
                         @Value("${app.invitaciones.web-url:http://localhost:5173}") String webUrl) {
        this.planRepository = planRepository;
        this.enviadaRepository = enviadaRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.membresiaRepository = membresiaRepository;
        this.carenciaService = carenciaService;
        this.autorizacion = autorizacion;
        this.email = email;
        this.reloj = reloj;
        this.diasProximos = Math.max(1, Math.min(diasProximos, 60));
        this.webUrl = webUrl;
    }

    /** Las alertas de hoy, para la web. */
    @Transactional(readOnly = true)
    public AlertasResponse alertas(UUID establecimientoId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));
        return calcular(establecimientoId, LocalDate.now(reloj));
    }

    AlertasResponse calcular(UUID establecimientoId, LocalDate hoy) {
        List<PlanResponse> vencidos = new ArrayList<>();
        List<PlanResponse> proximos = new ArrayList<>();
        for (TratamientoPlanificado p : planRepository.pendientesHasta(
                establecimientoId, EstadoPlan.PENDIENTE, hoy.plusDays(diasProximos))) {
            (p.getFecha().isBefore(hoy) ? vencidos : proximos).add(PlanResponse.from(p, hoy));
        }
        return new AlertasResponse(hoy, diasProximos, vencidos, proximos, carencias(establecimientoId, hoy));
    }

    private AlertasResponse.Carencias carencias(UUID establecimientoId, LocalDate hoy) {
        Map<UUID, Animal> animales = planRepository.animalesActivos(establecimientoId, EstadoAnimal.ACTIVO).stream()
            .collect(Collectors.toMap(Animal::getId, Function.identity()));
        Map<UUID, CarenciaResponse> carencias = carenciaService.deAnimales(establecimientoId, animales.keySet());
        LocalDate limite = hoy.plusDays(diasProximos);
        List<AlertasResponse.AnimalCarencia> salen = carencias.entrySet().stream()
            .filter(e -> !e.getValue().libreDesde().isAfter(limite))
            .map(e -> new AlertasResponse.AnimalCarencia(e.getKey(), animales.get(e.getKey()).getCaravana(),
                e.getValue().libreDesde(),
                e.getValue().tratamientos().stream().map(CarenciaResponse.Tratamiento::producto).distinct()
                    .collect(Collectors.joining(", "))))
            .sorted(Comparator.comparing(AlertasResponse.AnimalCarencia::libreDesde)
                .thenComparing(AlertasResponse.AnimalCarencia::caravana))
            .toList();
        return new AlertasResponse.Carencias(carencias.size(), salen);
    }

    // ---------- Aviso diario por email ----------

    /**
     * Manda el aviso del día de cada establecimiento de la organización actual (el job la fija). Solo
     * hay aviso si hoy pasa algo: llega el día de avisar de un tratamiento, es el día del
     * tratamiento, ayer quedó vencido, o mañana un animal sale de carencia. Lo reciben los miembros
     * que pueden registrar sanidad y no desactivaron los emails; una vez por día.
     *
     * @return cuántos emails se mandaron
     */
    @Transactional
    public int enviarDelDia(LocalDate hoy) {
        int enviados = 0;
        for (Establecimiento est : establecimientoRepository.findAll()) {
            if (!est.isActivo()) continue;
            try {
                enviados += enviarDe(est, hoy);
            } catch (RuntimeException e) {
                log.error("Alertas {}: falló el establecimiento {}", hoy, est.getId(), e);
            }
        }
        return enviados;
    }

    private int enviarDe(Establecimiento est, LocalDate hoy) {
        // Hasta 30 días: el máximo de anticipación de un aviso
        List<TratamientoPlanificado> pendientes = planRepository.pendientesHasta(
            est.getId(), EstadoPlan.PENDIENTE, hoy.plusDays(30));
        boolean hayAvisoHoy = pendientes.stream().anyMatch(p ->
            p.getFecha().minusDays(p.getAvisarDiasAntes()).equals(hoy)
                || p.getFecha().equals(hoy)
                || p.getFecha().equals(hoy.minusDays(1)));
        AlertasResponse alertas = calcular(est.getId(), hoy);
        boolean salenManana = alertas.carencias().salenProximamente().stream()
            .anyMatch(c -> c.libreDesde().equals(hoy.plusDays(1)));
        if (!hayAvisoHoy && !salenManana) return 0;

        // Los que tienen el aviso de hoy aunque falten más días que los "próximos"
        List<PlanResponse> anticipados = pendientes.stream()
            .filter(p -> p.getFecha().isAfter(hoy.plusDays(diasProximos))
                && p.getFecha().minusDays(p.getAvisarDiasAntes()).equals(hoy))
            .map(p -> PlanResponse.from(p, hoy))
            .toList();

        int enviados = 0;
        for (Membresia m : membresiaRepository.findDelEstablecimiento(est.getId())) {
            Usuario u = m.getUsuario();
            if (!m.getRol().puede(Permiso.REGISTRAR_SANIDAD) || !u.isActivo() || !u.isAlertasEmail()) continue;
            if (enviadaRepository.existsByEstablecimientoIdAndUsuarioIdAndFecha(est.getId(), u.getId(), hoy)) continue;
            try {
                email.enviar(u.getEmail(), "Alertas sanitarias de " + est.getNombre() + " - " + hoy.format(FECHA),
                    texto(u, est, alertas, anticipados, hoy));
                enviadaRepository.save(new AlertaEnviada(est.getId(), u.getId(), hoy));
                enviados++;
            } catch (RuntimeException e) {
                log.error("Alertas {}: no se pudo mandar el email a {}", hoy, u.getId(), e);
            }
        }
        return enviados;
    }

    private String texto(Usuario u, Establecimiento est, AlertasResponse a, List<PlanResponse> anticipados,
                         LocalDate hoy) {
        StringBuilder t = new StringBuilder();
        t.append("Hola ").append(u.getNombre()).append(",\n\n");
        t.append("Esto es lo que hay que tener en cuenta en ").append(est.getNombre()).append(":\n");

        List<PlanResponse> deHoy = a.tratamientosProximos().stream().filter(p -> p.fecha().equals(hoy)).toList();
        List<PlanResponse> siguientes = new ArrayList<>(
            a.tratamientosProximos().stream().filter(p -> p.fecha().isAfter(hoy)).toList());
        siguientes.addAll(anticipados);

        seccion(t, "Tratamientos vencidos (no se registraron)", a.tratamientosVencidos(), hoy);
        seccion(t, "Tratamientos para hoy", deHoy, hoy);
        seccion(t, "Próximos tratamientos", siguientes, hoy);

        var salen = a.carencias().salenProximamente();
        if (!salen.isEmpty()) {
            t.append("\nAnimales que terminan la carencia (desde ese día se pueden vender o faenar):\n");
            for (var c : salen) {
                t.append("- ").append(c.libreDesde().format(DIA)).append(": ").append(c.caravana())
                    .append(" (").append(c.productos()).append(")\n");
            }
        }

        t.append("\nVer el calendario sanitario: ").append(webUrl).append("/calendario-sanitario\n\n");
        t.append("Recibís este aviso porque podés registrar sanidad en ").append(est.getNombre())
            .append(". Para dejar de recibirlo, desactivá los avisos por email en el calendario sanitario.\n");
        return t.toString();
    }

    private static void seccion(StringBuilder t, String titulo, List<PlanResponse> planes, LocalDate hoy) {
        if (planes.isEmpty()) return;
        t.append('\n').append(titulo).append(":\n");
        for (PlanResponse p : planes) {
            t.append("- ").append(p.fecha().format(DIA));
            long dias = ChronoUnit.DAYS.between(hoy, p.fecha());
            if (dias > 0) t.append(" (en ").append(dias).append(dias == 1 ? " día)" : " días)");
            t.append(": ").append(p.descripcion()).append(" · ").append(destino(p));
            if (p.dosis() != null) t.append(" · ").append(p.dosis());
            t.append('\n');
        }
    }

    private static String destino(PlanResponse p) {
        return switch (p.destino().tipo()) {
            case "LOTE" -> "Lote " + p.destino().nombre();
            case "RODEO" -> "Rodeo " + p.destino().nombre();
            default -> "Todo el establecimiento";
        };
    }
}
