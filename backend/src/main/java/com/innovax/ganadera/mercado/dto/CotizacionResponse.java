package com.innovax.ganadera.mercado.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.innovax.ganadera.mercado.Cotizacion;
import com.innovax.ganadera.mercado.EstadoCotizacion;
import com.innovax.ganadera.mercado.Producto;

/**
 * Cotización de un producto. {@code fecha}: día de la foto; {@code fechaDato}: la fecha que informa
 * la fuente. Sin {@code valor} = nunca se pudo leer ({@code estado} SIN_DATO).
 */
public record CotizacionResponse(
    Producto producto,
    Producto.Grupo grupo,
    String unidad,
    BigDecimal valor,
    LocalDate fecha,
    LocalDate fechaDato,
    String fuente,
    EstadoCotizacion estado,
    String nota
) {

    public static CotizacionResponse from(Cotizacion c) {
        Producto p = c.getProducto();
        return new CotizacionResponse(p, p.grupo(), p.unidad(), c.getValor(), c.getFecha(), c.getFechaDato(),
            c.getFuente(), c.getEstado(), c.getNota());
    }

    /** Producto que todavía no tiene ninguna foto (por ejemplo, sin fuente configurada). */
    public static CotizacionResponse sinDato(Producto p) {
        return new CotizacionResponse(p, p.grupo(), p.unidad(), null, null, null, null,
            EstadoCotizacion.SIN_DATO, null);
    }
}
