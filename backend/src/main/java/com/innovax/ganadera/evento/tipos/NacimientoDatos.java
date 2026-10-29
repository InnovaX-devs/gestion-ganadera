package com.innovax.ganadera.evento.tipos;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.Sexo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Issue #18. Un parto de la madre (el animal del evento). Una cría por elemento de {@code crias}:
 * mellizos son dos crías en el mismo nacimiento, no dos nacimientos.
 *
 * <pre>
 * { "tipoParto": "ASISTIDO",
 *   "crias": [
 *     { "sexo": "MACHO",  "caravana": "C-101", "pesoKg": 34.5 },
 *     { "sexo": "HEMBRA", "nacidoMuerto": true } ],
 *   "observaciones": "Venía de cola" }
 * </pre>
 *
 * Las crías vivas se crean como animales; las nacidas muertas quedan solo acá (no llevan caravana).
 */
public record NacimientoDatos(
    @NotNull(message = "Elegí el tipo de parto")
    TipoParto tipoParto,

    @NotEmpty(message = "Indicá al menos una cría")
    @Size(max = 3, message = "Se pueden registrar hasta 3 crías por parto")
    List<@NotNull(message = "Faltan los datos de la cría") @Valid Cria> crias,

    @Size(max = 500, message = "Máximo 500 caracteres")
    String observaciones
) {

    public NacimientoDatos {
        observaciones = limpiar(observaciones);
    }

    /** Las crías que nacieron vivas: son las que se crean como animales. */
    @JsonIgnore
    public List<Cria> vivas() {
        return crias == null ? List.of() : crias.stream().filter(c -> c != null && !c.nacidoMuerto()).toList();
    }

    /**
     * @param caravana obligatoria si nació viva (se valida en el handler, que sabe cuál es cuál); en
     *                 las nacidas muertas se descarta.
     * @param pesoKg   opcional: no siempre se pesa al nacer.
     */
    public record Cria(
        @NotNull(message = "Elegí el sexo")
        Sexo sexo,

        boolean nacidoMuerto,

        @Size(max = 30, message = "Máximo 30 caracteres")
        String caravana,

        @Positive(message = "El peso tiene que ser mayor a 0")
        @DecimalMax(value = "100", message = "Revisá el peso: más de 100 kg al nacer no es posible")
        BigDecimal pesoKg
    ) {

        public Cria {
            caravana = nacidoMuerto ? null : Animal.normalizarCaravana(caravana);
        }
    }

    private static String limpiar(String valor) {
        if (valor == null) return null;
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
