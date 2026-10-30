package com.innovax.ganadera.evento.tipos;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;

import lombok.RequiredArgsConstructor;

/**
 * Baja (#23). El animal sale del stock por muerte (queda MUERTO) o pérdida (queda PERDIDO). Se puede
 * dar de baja a varios a la vez, o a todo un lote o rodeo (como en los demás eventos).
 *
 * <p>Si se cargó por error se anula el evento y el animal vuelve al stock, con su lote y rodeo de
 * antes (la baja no los toca). Corta si en el medio otro animal tomó su caravana.
 */
@Component
@RequiredArgsConstructor
public class BajaHandler implements TipoEventoHandler<BajaDatos> {

    private final AnimalRepository animalRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BAJA;
    }

    @Override
    public Class<BajaDatos> claseDatos() {
        return BajaDatos.class;
    }

    @Override
    public void validar(BajaDatos datos, Contexto contexto) {
        if (datos.motivo() == MotivoBaja.MUERTE && datos.causa() == null) {
            throw new BusinessException("Indicá la causa de la muerte");
        }
    }

    @Override
    public void aplicar(Evento evento, BajaDatos datos) {
        SalidaDelStock.sacar(evento.getAnimal(), datos.motivo().estado());
    }

    @Override
    public void revertir(Evento evento, BajaDatos datos) {
        SalidaDelStock.devolver(evento.getAnimal(), datos.motivo().estado(), "la baja", animalRepository);
    }
}
