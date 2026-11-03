package com.innovax.ganadera.calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aviso diario de alertas sanitarias por email (#34). Recorre las organizaciones de a una, con su
 * organización fijada (como {@code CambioCategoriaJob}, ADR 0003): un error en una no frena a las
 * demás.
 *
 * <p>Horario: {@code app.jobs.alertas-cron} (por defecto, todos los días a las 7, hora de
 * Argentina). Se apaga con {@code app.jobs.habilitados=false}.
 *
 * <p>Hoy el único canal es el email. Push y WhatsApp quedan para más adelante: van a necesitar una
 * interfaz de canal parecida a {@code EmailSender}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertasJob {

    private final OrganizacionRepository organizacionRepository;
    private final AlertaService alertaService;
    private final Clock reloj;

    @Scheduled(cron = "${app.jobs.alertas-cron:0 0 7 * * *}", zone = CategoriaConfig.ZONA_HORARIA)
    public void ejecutarDiario() {
        ejecutar(LocalDate.now(reloj));
    }

    /** @return cuántos emails se mandaron en total */
    public int ejecutar(LocalDate hoy) {
        List<UUID> organizaciones = organizacionRepository.findAll().stream().map(Organizacion::getId).toList();
        int total = 0;
        int conError = 0;
        for (UUID organizacionId : organizaciones) {
            try {
                total += TenantContext.comoOrganizacion(organizacionId, () -> alertaService.enviarDelDia(hoy));
            } catch (RuntimeException e) {
                conError++;
                log.error("Alertas sanitarias: falló la organización {}", organizacionId, e);
            }
        }
        log.info("Alertas sanitarias ({}): {} emails, {} organizaciones, {} con error",
            hoy, total, organizaciones.size(), conError);
        return total;
    }
}
