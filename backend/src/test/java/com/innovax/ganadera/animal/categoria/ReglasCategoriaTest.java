package com.innovax.ganadera.animal.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.categoria.ReglasCategoria.Transicion;

/** Issue #16: una prueba (o más) por cada regla, sin Spring ni base. Umbrales por defecto: 8 y 24 meses, 400 y 450 kg. */
class ReglasCategoriaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);

    private final ReglasCategoria reglas = new ReglasCategoria(ParametrosCategoria.POR_DEFECTO);

    @Nested
    class TerneraAVaquillona {

        @Test
        void alDestete() {
            assertThat(siguiente(Categoria.TERNERA, false, null, new Hecho.Destete(HOY)))
                .contains(new Transicion(Categoria.VAQUILLONA, MotivoCambio.DESTETE));
        }

        @Test
        void porEdadAlCumplirOchoMeses() {
            assertThat(porEdad(Categoria.TERNERA, false, HOY.minusMonths(8)))
                .contains(new Transicion(Categoria.VAQUILLONA, MotivoCambio.EDAD));
        }

        @Test
        void unDiaAntesDeLosOchoMesesSigueSiendoTernera() {
            assertThat(porEdad(Categoria.TERNERA, false, HOY.minusMonths(8).plusDays(1))).isEmpty();
        }
    }

    @Nested
    class VaquillonaAVaca {

        @Test
        void alRegistrarSuPrimerParto() {
            assertThat(siguiente(Categoria.VAQUILLONA, false, null, new Hecho.Parto(HOY)))
                .contains(new Transicion(Categoria.VACA, MotivoCambio.PARTO));
        }

        @Test
        void siFiguraComoTerneraYPareTambienPasaAVaca() {
            assertThat(siguiente(Categoria.TERNERA, false, HOY.minusMonths(20), new Hecho.Parto(HOY)))
                .contains(new Transicion(Categoria.VACA, MotivoCambio.PARTO));
        }

        @Test
        void losPartosSiguientesNoCambianNada() {
            assertThat(siguiente(Categoria.VACA, false, null, new Hecho.Parto(HOY))).isEmpty();
        }

        @Test
        void laVaquillonaNoPasaAVacaPorEdad() {
            assertThat(porEdad(Categoria.VAQUILLONA, false, HOY.minusYears(5))).isEmpty();
        }

        @Test
        void niPorElDestete() {
            assertThat(siguiente(Categoria.VAQUILLONA, false, null, new Hecho.Destete(HOY))).isEmpty();
        }
    }

    @Nested
    class TerneroANovillitoOTorito {

        @ParameterizedTest
        @CsvSource({"true, NOVILLITO", "false, TORITO"})
        void alDesteteSegunSiEstaCastrado(boolean castrado, Categoria esperada) {
            assertThat(siguiente(Categoria.TERNERO, castrado, null, new Hecho.Destete(HOY)))
                .contains(new Transicion(esperada, MotivoCambio.DESTETE));
        }

        @ParameterizedTest
        @CsvSource({"true, NOVILLITO", "false, TORITO"})
        void porEdadSegunSiEstaCastrado(boolean castrado, Categoria esperada) {
            assertThat(porEdad(Categoria.TERNERO, castrado, HOY.minusMonths(8)))
                .contains(new Transicion(esperada, MotivoCambio.EDAD));
        }

        @Test
        void conMenosDeOchoMesesSigueSiendoTernero() {
            assertThat(porEdad(Categoria.TERNERO, false, HOY.minusMonths(7))).isEmpty();
        }

        @Test
        void unPesajeNoLoCambia() {
            assertThat(siguiente(Categoria.TERNERO, false, null, new Hecho.Pesaje(HOY, new BigDecimal("600"))))
                .isEmpty();
        }
    }

    @Nested
    class NovillitoANovillo {

        @Test
        void porEdadALosDosAnios() {
            assertThat(porEdad(Categoria.NOVILLITO, true, HOY.minusMonths(24)))
                .contains(new Transicion(Categoria.NOVILLO, MotivoCambio.EDAD));
        }

        @Test
        void conVeintitresMesesSigueSiendoNovillito() {
            assertThat(porEdad(Categoria.NOVILLITO, true, HOY.minusMonths(23))).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({"400, true", "450.5, true", "399.9, false"})
        void porPeso(String kilos, boolean cambia) {
            Optional<Transicion> resultado =
                siguiente(Categoria.NOVILLITO, true, null, new Hecho.Pesaje(HOY, new BigDecimal(kilos)));
            if (cambia) {
                assertThat(resultado).contains(new Transicion(Categoria.NOVILLO, MotivoCambio.PESO));
            } else {
                assertThat(resultado).isEmpty();
            }
        }
    }

    @Nested
    class ToritoAToro {

        @Test
        void porEdadALosDosAnios() {
            assertThat(porEdad(Categoria.TORITO, false, HOY.minusMonths(24)))
                .contains(new Transicion(Categoria.TORO, MotivoCambio.EDAD));
        }

        @Test
        void conVeintitresMesesSigueSiendoTorito() {
            assertThat(porEdad(Categoria.TORITO, false, HOY.minusMonths(23))).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({"450, true", "449.99, false"})
        void porPeso(String kilos, boolean cambia) {
            Optional<Transicion> resultado =
                siguiente(Categoria.TORITO, false, null, new Hecho.Pesaje(HOY, new BigDecimal(kilos)));
            if (cambia) {
                assertThat(resultado).contains(new Transicion(Categoria.TORO, MotivoCambio.PESO));
            } else {
                assertThat(resultado).isEmpty();
            }
        }
    }

    @Nested
    class CasosGenerales {

        @ParameterizedTest
        @EnumSource(value = Categoria.class, names = {"VACA", "NOVILLO", "TORO"})
        void lasCategoriasFinalesNoCambianConNingunHecho(Categoria categoria) {
            LocalDate viejo = HOY.minusYears(10);
            assertThat(siguiente(categoria, false, viejo, new Hecho.Destete(HOY))).isEmpty();
            assertThat(siguiente(categoria, false, viejo, new Hecho.Parto(HOY))).isEmpty();
            assertThat(siguiente(categoria, false, viejo, new Hecho.Pesaje(HOY, new BigDecimal("900")))).isEmpty();
            assertThat(porEdad(categoria, false, viejo)).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = Categoria.class, names = {"TERNERA", "TERNERO", "NOVILLITO", "TORITO"})
        void sinFechaDeNacimientoNoCambiaPorEdad(Categoria categoria) {
            assertThat(porEdad(categoria, false, null)).isEmpty();
        }

        @Test
        void unaFechaAnteriorAlNacimientoNoCuentaComoEdad() {
            assertThat(reglas.siguiente(Categoria.TORITO, false, HOY,
                new Hecho.PasoDelTiempo(HOY.minusYears(3)))).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = Categoria.class, names = {"TERNERO", "NOVILLITO", "TORITO"})
        void unPartoNoCambiaAUnMacho(Categoria categoria) {
            assertThat(siguiente(categoria, false, null, new Hecho.Parto(HOY))).isEmpty();
        }

        @Test
        void losUmbralesSonConfigurables() {
            var reglasPropias = new ReglasCategoria(
                new ParametrosCategoria(6, 18, new BigDecimal("350"), new BigDecimal("380")));

            assertThat(reglasPropias.siguiente(Categoria.TERNERO, false, HOY.minusMonths(6),
                new Hecho.PasoDelTiempo(HOY))).map(Transicion::categoriaNueva).contains(Categoria.TORITO);
            assertThat(reglasPropias.siguiente(Categoria.NOVILLITO, true, HOY.minusMonths(18),
                new Hecho.PasoDelTiempo(HOY))).map(Transicion::categoriaNueva).contains(Categoria.NOVILLO);
            assertThat(reglasPropias.siguiente(Categoria.TORITO, false, null,
                new Hecho.Pesaje(HOY, new BigDecimal("380")))).map(Transicion::categoriaNueva).contains(Categoria.TORO);
            assertThat(reglasPropias.mesesMinimosParaCambiarPorEdad()).isEqualTo(6);
        }

        @Test
        void parametrosInvalidosNoDejanArrancar() {
            assertThatThrownBy(() -> new ParametrosCategoria(0, 24, null, null))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> new ParametrosCategoria(24, 8, null, null))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> new ParametrosCategoria(null, null, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    private Optional<Transicion> siguiente(Categoria categoria, boolean castrado, LocalDate nacimiento, Hecho hecho) {
        return reglas.siguiente(categoria, castrado, nacimiento, hecho);
    }

    private Optional<Transicion> porEdad(Categoria categoria, boolean castrado, LocalDate nacimiento) {
        return reglas.siguiente(categoria, castrado, nacimiento, new Hecho.PasoDelTiempo(HOY));
    }
}
