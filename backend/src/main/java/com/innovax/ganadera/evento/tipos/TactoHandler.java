package com.innovax.ganadera.evento.tipos;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;
import com.innovax.ganadera.reproduccion.Gestacion;

import lombok.RequiredArgsConstructor;

/**
 * Tacto (#19): preñada (con meses de gestación) o vacía, sobre vacas y vaquillonas. Con la
 * preñez, el servidor calcula la fecha estimada de parto (ver {@link Gestacion}) y la guarda en el
 * evento.
 *
 * <p>Como el servicio, no cambia nada del animal: el estado reproductivo sale de los eventos.
 */
@Component
@RequiredArgsConstructor
public class TactoHandler implements TipoEventoHandler<TactoDatos> {

    /**
     * Margen de la estimación de meses: un parto registrado hasta un mes después de la concepción
     * estimada se tolera (el tacto no es exacto); uno posterior es incompatible con esa preñez.
     */
    static final int TOLERANCIA_DIAS = 30;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EventoRepository eventoRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.TACTO;
    }

    @Override
    public Class<TactoDatos> claseDatos() {
        return TactoDatos.class;
    }

    @Override
    public boolean aplicaA(Animal animal) {
        return Reproduccion.esHembraDeCria(animal);
    }

    @Override
    public void validar(TactoDatos datos, Contexto contexto) {
        Reproduccion.exigirHembrasDeCria(contexto.animales(), "tacto");
        if (!datos.prenada()) return;
        if (datos.mesesGestacion() == null) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("datos.mesesGestacion", "Indicá los meses de gestación")));
        }
        validarPartosPosteriores(datos.mesesGestacion(), contexto);
    }

    /**
     * Una vaca que parió después de quedar preñada (según los meses del tacto) no puede tener esa
     * preñez: o los meses o la fecha están mal.
     */
    private void validarPartosPosteriores(int meses, Contexto contexto) {
        LocalDate concepcion = Gestacion.concepcionEstimada(contexto.fecha(), meses);
        LocalDate desde = concepcion.plusDays(TOLERANCIA_DIAS);
        if (desde.isAfter(contexto.fecha())) return;
        List<UUID> ids = contexto.animales().stream().map(Animal::getId).toList();
        eventoRepository.buscarDeAnimalesDesde(TipoEvento.NACIMIENTO, ids, desde.minusDays(1)).stream()
            .filter(parto -> !parto.getFecha().isAfter(contexto.fecha()))
            .findFirst()
            .ifPresent(parto -> {
                throw new BusinessException(caravana(parto) + " parió el " + FECHA.format(parto.getFecha())
                    + ": no puede estar preñada de " + meses + (meses == 1 ? " mes" : " meses")
                    + " al " + FECHA.format(contexto.fecha()) + ". Revisá los meses o la fecha.");
            });
    }

    @Override
    public TactoDatos completar(TactoDatos datos, Contexto contexto) {
        return datos.completado(datos.prenada()
            ? Gestacion.fechaEstimadaParto(contexto.fecha(), datos.mesesGestacion())
            : null);
    }

    private static String caravana(Evento parto) {
        return parto.getAnimal().getCaravana();
    }
}
