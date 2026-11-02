package com.innovax.ganadera.evento.tipos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Issue #19. Un servicio: natural (con toros) o inseminación (con pajuela o semen de un toro). La
 * fecha del evento es el inicio; {@code fechaFin}, el día que se sacaron los toros (o el último de
 * un protocolo de inseminación).
 *
 * <pre>
 * { "tipo": "NATURAL", "fechaFin": "2027-01-31", "toroIds": ["..."] }
 * { "tipo": "INSEMINACION", "pajuela": "Angus Rito 707 - partida 1234" }
 * </pre>
 *
 * <p>El cliente manda {@code toroIds}; {@code toros} (id y caravana) lo completa el servidor, así el
 * historial conserva qué toros fueron aunque después se vendan.
 *
 * @param fechaFin opcional: un servicio natural sin fin se considera en curso hasta el tacto. Puede
 *                 ser futura (se carga cuando entran los toros, con la fecha prevista de salida).
 * @param pajuela  solo en inseminación: identificación del semen (toro, central, partida).
 */
public record ServicioDatos(
    @NotNull(message = "Elegí si fue natural o inseminación")
    TipoServicio tipo,

    LocalDate fechaFin,

    @Size(max = ServicioDatos.MAX_TOROS, message = "Se pueden indicar hasta 10 toros")
    List<@NotNull(message = "Falta el toro") UUID> toroIds,

    List<Toro> toros,

    @Size(max = 100, message = "Máximo 100 caracteres")
    String pajuela,

    @Size(max = 500, message = "Máximo 500 caracteres")
    String observaciones
) {

    public static final int MAX_TOROS = 10;

    /** Un servicio no dura más que esto: más largo es casi seguro un error en la fecha de fin. */
    public static final int MAX_DIAS = 365;

    public ServicioDatos {
        toroIds = toroIds == null ? List.of() : toroIds.stream().distinct().toList();
        pajuela = Reproduccion.limpiar(pajuela);
        observaciones = Reproduccion.limpiar(observaciones);
    }

    /** Copia con los toros resueltos por el servidor. La pajuela no aplica al servicio natural. */
    public ServicioDatos completado(List<Toro> toros) {
        return new ServicioDatos(tipo, fechaFin, toroIds, toros,
            tipo == TipoServicio.INSEMINACION ? pajuela : null, observaciones);
    }

    /** Un toro del servicio, con la caravana que tenía al registrarlo. */
    public record Toro(UUID id, String caravana) {
    }
}
