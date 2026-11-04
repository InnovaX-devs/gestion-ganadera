package com.innovax.ganadera.mercado;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.mercado.fuente.FuenteCotizacion;
import com.innovax.ganadera.mercado.fuente.Lectura;

import lombok.extern.slf4j.Slf4j;

/**
 * Job diario de cotizaciones (#27). Consulta cada fuente por separado: si una falla, sus productos
 * mantienen el último valor y el error queda guardado y en el log; las demás se guardan igual.
 *
 * <p>Horario: {@code app.jobs.cotizaciones-cron} (por defecto, todos los días a las 19, hora de
 * Argentina: ya cerró el dólar y está la pizarra del día hábil anterior). Además, si la tabla está
 * vacía corre una vez al arrancar, para no esperar hasta la noche. Se apaga con
 * {@code app.jobs.habilitados=false}.
 */
@Slf4j
@Component
public class ActualizacionCotizacionesJob {

    private final List<FuenteCotizacion> fuentes;
    private final CotizacionService service;
    private final Clock reloj;
    private final boolean habilitado;

    /** Con {@code app.mercado.fuentes-externas=false} y sin fuentes de prueba, la lista queda vacía. */
    public ActualizacionCotizacionesJob(ObjectProvider<FuenteCotizacion> fuentes, CotizacionService service,
                                        Clock reloj, @Value("${app.jobs.habilitados:true}") boolean habilitado) {
        this.fuentes = fuentes.orderedStream().toList();
        this.service = service;
        this.reloj = reloj;
        this.habilitado = habilitado;
    }

    @Scheduled(cron = "${app.jobs.cotizaciones-cron:0 0 19 * * *}", zone = CategoriaConfig.ZONA_HORARIA)
    public void ejecutarDiario() {
        ejecutar(LocalDate.now(reloj));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        if (habilitado && !service.hayCotizaciones()) {
            log.info("Cotizaciones: no hay ninguna guardada, se buscan ahora");
            ejecutarDiario();
        }
    }

    /** @return cuántas fuentes fallaron por completo */
    public int ejecutar(LocalDate hoy) {
        int fallidas = 0;
        for (FuenteCotizacion fuente : fuentes) {
            List<Lectura> lecturas = List.of();
            String error = null;
            try {
                lecturas = fuente.leer();
            } catch (RuntimeException e) {
                fallidas++;
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error("Cotizaciones {}: falló la fuente {}", hoy, fuente.nombre(), e);
            }
            try {
                service.registrar(hoy, fuente.nombre(), fuente.productos(), lecturas, error);
            } catch (RuntimeException e) {
                log.error("Cotizaciones {}: no se pudo guardar lo de {}", hoy, fuente.nombre(), e);
            }
        }
        log.info("Cotizaciones {}: {} fuentes, {} fallaron", hoy, fuentes.size(), fallidas);
        return fallidas;
    }
}
