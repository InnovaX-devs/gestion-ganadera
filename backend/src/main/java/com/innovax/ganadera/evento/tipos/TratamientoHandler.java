package com.innovax.ganadera.evento.tipos;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;
import com.innovax.ganadera.sanidad.ProductoSanitario;
import com.innovax.ganadera.sanidad.ProductoSanitarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tratamiento sanitario (#20). No modifica al animal: guarda qué se aplicó y, con la fecha y los
 * días de carencia, de ahí sale si el animal está en carencia (ver {@code sanidad.CarenciaService}).
 */
@Component
@RequiredArgsConstructor
public class TratamientoHandler implements TipoEventoHandler<TratamientoDatos> {

    private final ProductoSanitarioRepository productos;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.TRATAMIENTO;
    }

    @Override
    public Class<TratamientoDatos> claseDatos() {
        return TratamientoDatos.class;
    }

    /** Toma nombre, tipo y (si no se indicó otra) carencia del producto del catálogo. */
    @Override
    public TratamientoDatos completar(TratamientoDatos datos, Contexto contexto) {
        ProductoSanitario producto = productos
            .findByIdAndEstablecimientoIdAndActivoTrue(datos.productoId(), contexto.establecimiento().getId())
            .orElseThrow(() -> new BusinessException("El producto elegido no existe en este establecimiento"));
        int carencia = datos.diasCarencia() != null ? datos.diasCarencia() : producto.getDiasCarencia();
        return datos.completado(producto.getNombre(), producto.getTipo(), carencia);
    }
}
