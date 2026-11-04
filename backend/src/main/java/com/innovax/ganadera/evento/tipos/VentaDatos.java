package com.innovax.ganadera.evento.tipos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Issue #23. La venta de uno o más animales: el animal pasa a VENDIDO y sale del stock.
 *
 * <p>El precio se pacta {@code POR_KG} o {@code POR_CABEZA}. El peso se indica de dos formas, igual
 * que en un pesaje (#21):
 * <ul>
 *   <li><b>Un animal</b> (o una grilla con un peso por animal): {@code kg}.</li>
 *   <li><b>Varios animales</b> (venta masiva): {@code pesoTotal} del grupo; cada animal queda con el
 *       promedio. Si el precio es por cabeza el peso es opcional.</li>
 * </ul>
 *
 * <p>El servidor completa {@code kg} (siempre, si hay peso), {@code pesoTotal}/{@code cantidad} de
 * la venta grupal y {@code importe} del animal (precio × kg, o el precio por cabeza); se ignora lo
 * que mande el cliente. {@code importe} se guarda para que el historial no cambie si después cambian
 * las reglas de cálculo.
 *
 * @param comprador         a quién se vende.
 * @param documento         DT-e (documento de tránsito electrónico) o guía, como texto libre. Opcional.
 * @param confirmarCarencia true para vender igual animales en carencia sanitaria (ver {@code CarenciaService}).
 *                          El servidor lo deja en true solo si hacía falta, para que quede constancia.
 */
public record VentaDatos(
    @NotBlank(message = "Indicá el comprador")
    @Size(max = 120, message = "Máximo 120 caracteres")
    String comprador,

    @NotNull(message = "Elegí si el precio es por kg o por cabeza")
    ModalidadPrecio modalidad,

    @NotNull(message = "Indicá el precio")
    @Positive(message = "El precio tiene que ser mayor a 0")
    @DecimalMax(value = "100000000", message = "El precio es demasiado alto")
    @Digits(integer = 9, fraction = 2, message = "Hasta 2 decimales")
    BigDecimal precio,

    @Positive(message = "El peso tiene que ser mayor a 0")
    @DecimalMax(value = "1500", message = "El peso máximo es 1500 kg")
    @Digits(integer = 4, fraction = 2, message = "Hasta 2 decimales")
    BigDecimal kg,

    @Positive(message = "El peso total tiene que ser mayor a 0")
    @DecimalMax(value = "1000000", message = "El peso total máximo es 1.000.000 kg")
    @Digits(integer = 7, fraction = 2, message = "Hasta 2 decimales")
    BigDecimal pesoTotal,

    Integer cantidad,

    @Size(max = 60, message = "Máximo 60 caracteres")
    String documento,

    Boolean confirmarCarencia,

    BigDecimal importe
) {

    public VentaDatos {
        comprador = comprador == null ? null : comprador.trim();
        documento = documento == null || documento.isBlank() ? null : documento.trim();
    }

    public boolean esGrupal() {
        return pesoTotal != null;
    }

    public boolean confirmaCarencia() {
        return Boolean.TRUE.equals(confirmarCarencia);
    }
}
