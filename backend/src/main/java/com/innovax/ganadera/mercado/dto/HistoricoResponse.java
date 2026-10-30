package com.innovax.ganadera.mercado.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.innovax.ganadera.mercado.EstadoCotizacion;
import com.innovax.ganadera.mercado.Producto;

/**
 * {@code GET /api/mercado/historico}: una serie por producto pedido (vacía si no hay datos en el
 * rango) y la relación de reposición de cada día en que hubo precio de ternero y de novillo.
 */
public record HistoricoResponse(
    LocalDate desde,
    LocalDate hasta,
    Map<Producto, List<Punto>> series,
    List<PuntoRelacion> relacionReposicion
) {

    public record Punto(LocalDate fecha, BigDecimal valor, LocalDate fechaDato, EstadoCotizacion estado) {
    }

    public record PuntoRelacion(LocalDate fecha, BigDecimal valor) {
    }
}
