package com.innovax.ganadera.mercado;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.innovax.ganadera.common.tenant.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Foto diaria de un producto (#27). Extiende {@link BaseEntity} y no {@code TenantEntity}: las
 * cotizaciones son las mismas para todas las organizaciones.
 */
@Getter
@Entity
@Table(name = "cotizacion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cotizacion extends BaseEntity {

    static final int LARGO_ERROR = 500;
    static final int LARGO_NOTA = 150;

    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    @Convert(converter = Producto.Converter.class)
    @Column(nullable = false, updatable = false, length = 20)
    private Producto producto;

    @Column(precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(name = "fecha_dato")
    private LocalDate fechaDato;

    @Column(nullable = false, length = 50)
    private String fuente;

    @Convert(converter = EstadoCotizacion.Converter.class)
    @Column(nullable = false, length = 10)
    private EstadoCotizacion estado;

    @Column(length = LARGO_NOTA)
    private String nota;

    @Column(length = LARGO_ERROR)
    private String error;

    public Cotizacion(LocalDate fecha, Producto producto) {
        this.fecha = fecha;
        this.producto = producto;
    }

    /** Valor leído hoy de la fuente. */
    void actual(BigDecimal valor, LocalDate fechaDato, String fuente, String nota) {
        this.valor = valor;
        this.fechaDato = fechaDato;
        this.fuente = fuente;
        this.estado = EstadoCotizacion.ACTUAL;
        this.nota = recortar(nota, LARGO_NOTA);
        this.error = null;
    }

    /** La fuente falló: se copia el último valor conocido, con la fecha que tenía en la fuente. */
    void anterior(Cotizacion ultima, String fuente, String error) {
        this.valor = ultima.getValor();
        this.fechaDato = ultima.getFechaDato();
        this.fuente = fuente;
        this.estado = EstadoCotizacion.ANTERIOR;
        this.nota = ultima.getNota();
        this.error = recortar(error, LARGO_ERROR);
    }

    /** La fuente falló y no hay ningún valor anterior. */
    void sinDato(String fuente, String error) {
        this.valor = null;
        this.fechaDato = null;
        this.fuente = fuente;
        this.estado = EstadoCotizacion.SIN_DATO;
        this.nota = null;
        this.error = recortar(error, LARGO_ERROR);
    }

    private static String recortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo);
    }
}
