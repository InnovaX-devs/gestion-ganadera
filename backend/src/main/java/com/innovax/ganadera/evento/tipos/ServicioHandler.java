package com.innovax.ganadera.evento.tipos;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;

import lombok.RequiredArgsConstructor;

/**
 * Servicio (#19): natural o por inseminación, sobre vacas y vaquillonas. Se carga de a una, por
 * selección o para todo un rodeo (o lote): en ese caso reciben el servicio solo las vacas y
 * vaquillonas del grupo, no los toros ni los terneros al pie.
 *
 * <p>No cambia nada del animal: el estado reproductivo sale de los eventos (ver
 * {@code reproduccion.EstadoReproductivoService}), así que anularlo no necesita deshacer nada.
 */
@Component
@RequiredArgsConstructor
public class ServicioHandler implements TipoEventoHandler<ServicioDatos> {

    private final AnimalRepository animalRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SERVICIO;
    }

    @Override
    public Class<ServicioDatos> claseDatos() {
        return ServicioDatos.class;
    }

    @Override
    public boolean aplicaA(Animal animal) {
        return Reproduccion.esHembraDeCria(animal);
    }

    @Override
    public void validar(ServicioDatos datos, Contexto contexto) {
        Reproduccion.exigirHembrasDeCria(contexto.animales(), "servicio");

        if (datos.fechaFin() != null) {
            String error = null;
            if (datos.fechaFin().isBefore(contexto.fecha())) {
                error = "No puede ser anterior al inicio";
            } else if (datos.fechaFin().isAfter(contexto.fecha().plusDays(ServicioDatos.MAX_DIAS))) {
                error = "Un servicio no dura más de un año: revisá la fecha";
            }
            if (error != null) {
                throw new DatosInvalidosException(List.of(new ApiError.FieldError("datos.fechaFin", error)));
            }
        }

        if (datos.tipo() == TipoServicio.INSEMINACION) {
            if (datos.toroIds().size() > 1) {
                throw new DatosInvalidosException(List.of(new ApiError.FieldError("datos.toroIds",
                    "En una inseminación va un solo toro (el del semen)")));
            }
            if (datos.pajuela() == null && datos.toroIds().isEmpty()) {
                throw new DatosInvalidosException(List.of(new ApiError.FieldError("datos.pajuela",
                    "Indicá la pajuela o el toro del semen")));
            }
        }
    }

    /** Resuelve los toros: activos del establecimiento y machos enteros (toro o torito). */
    @Override
    public ServicioDatos completar(ServicioDatos datos, Contexto contexto) {
        if (datos.toroIds().isEmpty()) {
            return datos.completado(null);
        }
        UUID establecimientoId = contexto.establecimiento().getId();
        Map<UUID, Animal> encontrados = animalRepository.findAllById(new HashSet<>(datos.toroIds())).stream()
            .filter(a -> a.getEstablecimiento().getId().equals(establecimientoId) && a.isActivo())
            .collect(Collectors.toMap(Animal::getId, Function.identity()));

        List<ServicioDatos.Toro> toros = datos.toroIds().stream().map(id -> {
            Animal toro = encontrados.get(id);
            if (toro == null) {
                throw new BusinessException("Alguno de los toros elegidos no está en este establecimiento");
            }
            if (toro.getCategoria() != Categoria.TORO && toro.getCategoria() != Categoria.TORITO) {
                throw new BusinessException(toro.getCaravana() + " no es toro ni torito");
            }
            return new ServicioDatos.Toro(toro.getId(), toro.getCaravana());
        }).toList();
        return datos.completado(toros);
    }
}
