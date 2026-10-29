package com.innovax.ganadera.evento.tipos;

import java.util.UUID;

import com.innovax.ganadera.sanidad.TipoProducto;
import com.innovax.ganadera.sanidad.ViaAdministracion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Issue #20. Un tratamiento sanitario (vacuna, antiparasitario, antibiótico u otro).
 *
 * <p>El cliente manda {@code productoId} y lo que se aplicó; {@code producto} y {@code tipo} los
 * completa el servidor con los del catálogo (se ignora lo que mande el cliente), así el historial
 * conserva lo que se aplicó aunque después se renombre o se dé de baja el producto.
 *
 * @param diasCarencia opcional. Si no viene se usa la carencia del producto; si viene (incluso 0), manda esta.
 */
public record TratamientoDatos(
    @NotNull(message = "Elegí el producto")
    UUID productoId,

    String producto,

    TipoProducto tipo,

    @NotBlank(message = "Indicá la dosis")
    @Size(max = 100, message = "Máximo 100 caracteres")
    String dosis,

    @NotNull(message = "Elegí la vía de aplicación")
    ViaAdministracion via,

    @Size(max = 100, message = "Máximo 100 caracteres")
    String veterinario,

    @Min(value = 0, message = "Los días de carencia no pueden ser negativos")
    @Max(value = TratamientoDatos.MAX_DIAS_CARENCIA, message = "Máximo 365 días de carencia")
    Integer diasCarencia
) {

    /** Tope de la carencia. El cálculo de carencia por animal mira hasta un año atrás. */
    public static final int MAX_DIAS_CARENCIA = 365;

    public TratamientoDatos {
        dosis = dosis == null ? null : dosis.trim();
        veterinario = veterinario == null || veterinario.isBlank() ? null : veterinario.trim();
    }

    /** Copia con lo que el servidor resuelve a partir del catálogo. */
    public TratamientoDatos completado(String producto, TipoProducto tipo, int diasCarencia) {
        return new TratamientoDatos(productoId, producto, tipo, dosis, via, veterinario, diasCarencia);
    }
}
