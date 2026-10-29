package com.innovax.ganadera.animal.categoria;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job diario de cambios de categoría por edad (issue #16). Recorre las organizaciones de a una: cada
 * una corre en su propia transacción y con su organización fijada, así el filtro multi-tenant sigue
 * funcionando y un error en una no frena a las demás.
 *
 * <p>Horario: {@code app.jobs.cambio-categoria-cron} (por defecto, todos los días a las 3 de la
 * mañana, hora de Argentina). Se apaga con {@code app.jobs.habilitados=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CambioCategoriaJob {

    private final OrganizacionRepository organizacionRepository;
    private final CategoriaService categoriaService;
    private final Clock reloj;

    @Scheduled(cron = "${app.jobs.cambio-categoria-cron:0 0 3 * * *}", zone = CategoriaConfig.ZONA_HORARIA)
    public void ejecutarDiario() {
        ejecutar(LocalDate.now(reloj));
    }

    /** @return cuántos animales cambiaron de categoría en total */
    public int ejecutar(LocalDate hoy) {
        List<UUID> organizaciones = organizacionRepository.findAll().stream().map(Organizacion::getId).toList();
        int total = 0;
        int conError = 0;
        for (UUID organizacionId : organizaciones) {
            try {
                total += TenantContext.comoOrganizacion(organizacionId, () -> categoriaService.recalcularPorEdad(hoy));
            } catch (RuntimeException e) {
                conError++;
                log.error("Cambio de categoría por edad: falló la organización {}", organizacionId, e);
            }
        }
        log.info("Cambio de categoría por edad ({}): {} animales cambiaron, {} organizaciones, {} con error",
            hoy, total, organizaciones.size(), conError);
        return total;
    }
}
