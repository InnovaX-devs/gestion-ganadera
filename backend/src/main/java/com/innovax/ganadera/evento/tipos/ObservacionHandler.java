package com.innovax.ganadera.evento.tipos;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;

/**
 * El tipo más simple: solo guarda el texto. No valida nada extra ni modifica al animal, por eso no
 * sobrescribe validar/aplicar/revertir.
 */
@Component
public class ObservacionHandler implements TipoEventoHandler<ObservacionDatos> {

    @Override
    public TipoEvento tipo() {
        return TipoEvento.OBSERVACION;
    }

    @Override
    public Class<ObservacionDatos> claseDatos() {
        return ObservacionDatos.class;
    }
}
