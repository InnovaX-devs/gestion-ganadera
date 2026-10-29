package com.innovax.ganadera.animal.categoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Algo que le pasó a un animal y puede cambiarle la categoría. Lo informan los módulos de eventos
 * (destete, parto, pesaje) y el job diario ({@link PasoDelTiempo}).
 */
public sealed interface Hecho {

    /** Cuándo pasó. Las reglas por edad calculan la edad a esta fecha. */
    LocalDate fecha();

    record Destete(LocalDate fecha) implements Hecho {
        public Destete {
            Objects.requireNonNull(fecha, "fecha");
        }
    }

    /** Un parto de la hembra (no hace falta indicar si es el primero: la regla lo deduce de la categoría). */
    record Parto(LocalDate fecha) implements Hecho {
        public Parto {
            Objects.requireNonNull(fecha, "fecha");
        }
    }

    record Pesaje(LocalDate fecha, BigDecimal kilos) implements Hecho {
        public Pesaje {
            Objects.requireNonNull(fecha, "fecha");
            Objects.requireNonNull(kilos, "kilos");
        }
    }

    /** Revisión por edad que hace el job diario. */
    record PasoDelTiempo(LocalDate fecha) implements Hecho {
        public PasoDelTiempo {
            Objects.requireNonNull(fecha, "fecha");
        }
    }
}
