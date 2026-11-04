package com.innovax.ganadera.evento.tipos;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.categoria.CategoriaService;
import com.innovax.ganadera.animal.categoria.Hecho;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;

import lombok.RequiredArgsConstructor;

/**
 * Pesaje (#21). Un pesaje individual puede cambiar la categoría (novillito → novillo, torito → toro
 * por peso, ver {@link CategoriaService}); uno grupal no, porque el promedio no dice cuánto pesa
 * cada animal.
 *
 * <p>Al anular un pesaje no se deshace un cambio de categoría que haya provocado: queda en el
 * historial de categorías y, si no corresponde, se corrige editando el animal.
 */
@Component
@RequiredArgsConstructor
public class PesajeHandler implements TipoEventoHandler<PesajeDatos> {

    private final CategoriaService categoriaService;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.PESAJE;
    }

    @Override
    public Class<PesajeDatos> claseDatos() {
        return PesajeDatos.class;
    }

    @Override
    public void validar(PesajeDatos datos, Contexto contexto) {
        if (datos.kg() != null) {
            if (contexto.animales().size() > 1) {
                throw new BusinessException(
                    "Un pesaje individual es de un solo animal. Para varios, usá la grilla o el pesaje grupal");
            }
            return;
        }
        if (datos.pesoTotal() == null || datos.cantidad() == null) {
            throw new BusinessException("Indicá el peso del animal, o el peso total y la cantidad pesada");
        }
        if (promedio(datos).signum() <= 0) {
            throw new BusinessException("El peso total es demasiado bajo para la cantidad indicada");
        }
    }

    /** Individual: queda con kg. Grupal: kg = peso total / cantidad, con 2 decimales. */
    @Override
    public PesajeDatos completar(PesajeDatos datos, Contexto contexto) {
        if (datos.kg() != null) {
            return new PesajeDatos(datos.kg(), null, null, false);
        }
        return new PesajeDatos(promedio(datos), datos.pesoTotal(), datos.cantidad(), true);
    }

    @Override
    public void aplicar(Evento evento, PesajeDatos datos) {
        if (datos.esGrupal()) return;
        categoriaService.aplicar(evento.getAnimal(), new Hecho.Pesaje(evento.getFecha(), datos.kg()));
    }

    private static BigDecimal promedio(PesajeDatos datos) {
        return datos.pesoTotal().divide(BigDecimal.valueOf(datos.cantidad()), 2, RoundingMode.HALF_UP);
    }
}
