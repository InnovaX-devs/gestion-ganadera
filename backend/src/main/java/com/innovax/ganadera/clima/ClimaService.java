package com.innovax.ganadera.clima;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.innovax.ganadera.clima.dto.ClimaResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;

import lombok.extern.slf4j.Slf4j;

/**
 * Clima del establecimiento (#29). Usa sus coordenadas; si no tiene, busca la localidad.
 *
 * <p>Los pronósticos se guardan en memoria un rato ({@code app.clima.cache}, 30 minutos por
 * defecto) por ubicación redondeada a ~1 km: varios usuarios del mismo campo, o campos vecinos,
 * comparten la consulta. Así se respeta el límite de la API gratuita de Open-Meteo.
 */
@Slf4j
@Service
public class ClimaService {

    static final String SIN_UBICACION =
        "El establecimiento no tiene ubicación: cargá la localidad o las coordenadas para ver el clima";
    /** Tope de entradas en memoria: con más, se vacía (alcanza de sobra para el uso esperado). */
    private static final int MAXIMO_EN_CACHE = 2_000;
    private static final Duration CACHE_LOCALIDADES = Duration.ofDays(1);

    private final EstablecimientoRepository establecimientoRepository;
    private final AutorizacionService autorizacion;
    private final ProveedorClima proveedor;
    private final ClimaProperties propiedades;
    private final Clock reloj;

    private final Map<String, EnCache<ClimaResponse>> pronosticos = new ConcurrentHashMap<>();
    private final Map<String, EnCache<Optional<ProveedorClima.Lugar>>> localidades = new ConcurrentHashMap<>();

    private record EnCache<T>(T valor, Instant vence) {
    }

    public ClimaService(EstablecimientoRepository establecimientoRepository, AutorizacionService autorizacion,
                        ProveedorClima proveedor, ClimaProperties propiedades, Clock reloj) {
        this.establecimientoRepository = establecimientoRepository;
        this.autorizacion = autorizacion;
        this.proveedor = proveedor;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * @throws BusinessException (422) si el establecimiento no tiene ubicación o no se encuentra
     *     su localidad
     * @throws ClimaNoDisponibleException (503) si Open-Meteo no responde
     */
    // Sin @Transactional a propósito: no se tiene una conexión a la base mientras se espera a Open-Meteo
    public ClimaResponse clima(UUID establecimientoId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        Establecimiento e = establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));

        ClimaResponse.Ubicacion ubicacion = ubicacion(e);
        String clave = String.format(Locale.ROOT, "%.2f,%.2f", ubicacion.latitud(), ubicacion.longitud());
        Instant ahora = reloj.instant();

        EnCache<ClimaResponse> guardado = pronosticos.get(clave);
        if (guardado != null && guardado.vence().isAfter(ahora)) {
            return conUbicacion(guardado.valor(), ubicacion);
        }

        ProveedorClima.Pronostico p;
        try {
            p = proveedor.pronostico(ubicacion.latitud(), ubicacion.longitud(), dias());
        } catch (RuntimeException ex) {
            log.warn("Clima: Open-Meteo falló para {}", clave, ex);
            // Mejor un pronóstico de hace un rato que ninguno
            if (guardado != null) return conUbicacion(guardado.valor(), ubicacion);
            throw new ClimaNoDisponibleException();
        }

        ClimaResponse respuesta = armar(p, ubicacion, ahora);
        if (pronosticos.size() > MAXIMO_EN_CACHE) pronosticos.clear();
        pronosticos.put(clave, new EnCache<>(respuesta, ahora.plus(duracionCache())));
        return respuesta;
    }

    private ClimaResponse.Ubicacion ubicacion(Establecimiento e) {
        if (e.getLatitud() != null && e.getLongitud() != null) {
            return new ClimaResponse.Ubicacion(e.getLatitud().doubleValue(), e.getLongitud().doubleValue(),
                "COORDENADAS", null);
        }
        if (e.getLocalidad() == null || e.getLocalidad().isBlank()) {
            throw new BusinessException(SIN_UBICACION);
        }
        ProveedorClima.Lugar lugar = buscarLocalidad(e.getLocalidad().trim(), e.getProvincia())
            .orElseThrow(() -> new BusinessException("No encontramos la localidad \"" + e.getLocalidad().trim()
                + "\": revisá cómo está escrita o cargá las coordenadas del establecimiento"));
        return new ClimaResponse.Ubicacion(lugar.latitud(), lugar.longitud(), "LOCALIDAD", lugar.nombre());
    }

    private Optional<ProveedorClima.Lugar> buscarLocalidad(String localidad, String provincia) {
        String clave = (localidad + "|" + Objects.toString(provincia, "")).toLowerCase(Locale.ROOT);
        Instant ahora = reloj.instant();
        EnCache<Optional<ProveedorClima.Lugar>> guardado = localidades.get(clave);
        if (guardado != null && guardado.vence().isAfter(ahora)) return guardado.valor();
        Optional<ProveedorClima.Lugar> lugar;
        try {
            lugar = proveedor.buscarLocalidad(localidad, provincia);
        } catch (RuntimeException ex) {
            log.warn("Clima: falló la búsqueda de la localidad {}", localidad, ex);
            throw new ClimaNoDisponibleException();
        }
        if (localidades.size() > MAXIMO_EN_CACHE) localidades.clear();
        localidades.put(clave, new EnCache<>(lugar, ahora.plus(CACHE_LOCALIDADES)));
        return lugar;
    }

    private static ClimaResponse armar(ProveedorClima.Pronostico p, ClimaResponse.Ubicacion ubicacion, Instant ahora) {
        var a = p.actual();
        var actual = new ClimaResponse.Actual(a.hora(), a.temperatura(), a.sensacionTermica(), a.humedad(),
            a.viento(), a.precipitacion(), a.codigo());
        var dias = p.dias().stream()
            .map(d -> new ClimaResponse.Dia(d.fecha(), d.codigo(), d.maxima(), d.minima(), d.lluviaMm(),
                d.probabilidadLluvia()))
            .toList();
        BigDecimal lluvia = p.dias().stream()
            .map(ProveedorClima.Dia::lluviaMm)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(1, RoundingMode.HALF_UP);
        return new ClimaResponse(ubicacion, actual, dias, lluvia, "Open-Meteo", ahora);
    }

    /** Dos campos pueden compartir el pronóstico: cada uno ve su propia ubicación. */
    private static ClimaResponse conUbicacion(ClimaResponse r, ClimaResponse.Ubicacion ubicacion) {
        return new ClimaResponse(ubicacion, r.actual(), r.dias(), r.lluviaEsperadaMm(), r.fuente(), r.consultadoEn());
    }

    private int dias() {
        return propiedades.dias() >= 1 && propiedades.dias() <= 16 ? propiedades.dias() : 5;
    }

    private Duration duracionCache() {
        return propiedades.cache() != null ? propiedades.cache() : Duration.ofMinutes(30);
    }

    /** Para los tests. */
    void vaciarCache() {
        pronosticos.clear();
        localidades.clear();
    }
}
