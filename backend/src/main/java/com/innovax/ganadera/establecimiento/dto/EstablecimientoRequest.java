package com.innovax.ganadera.establecimiento.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Alta y edición. Solo el nombre es obligatorio. */
public record EstablecimientoRequest(
    @NotBlank(message = "Ingresá el nombre del establecimiento")
    @Size(max = 150, message = "Máximo 150 caracteres")
    String nombre,

    @Size(max = 100, message = "Máximo 100 caracteres")
    String localidad,

    @Size(max = 100, message = "Máximo 100 caracteres")
    String provincia,

    @Positive(message = "La superficie tiene que ser mayor a 0")
    @Digits(integer = 10, fraction = 2, message = "Superficie inválida (hasta 2 decimales)")
    BigDecimal superficieHa,

    @Size(max = 30, message = "Máximo 30 caracteres")
    String renspa,

    @DecimalMin(value = "-90", message = "Latitud inválida")
    @DecimalMax(value = "90", message = "Latitud inválida")
    BigDecimal latitud,

    @DecimalMin(value = "-180", message = "Longitud inválida")
    @DecimalMax(value = "180", message = "Longitud inválida")
    BigDecimal longitud
) {

    public EstablecimientoRequest {
        nombre = limpiar(nombre);
        localidad = limpiar(localidad);
        provincia = limpiar(provincia);
        renspa = limpiar(renspa);
    }

    /** Recorta espacios y convierte el texto vacío en null. */
    private static String limpiar(String valor) {
        if (valor == null) return null;
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
