package com.innovax.ganadera.mercado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.mercado.fuente.FuenteCotizacion;
import com.innovax.ganadera.mercado.fuente.Lectura;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/**
 * Issue #27 de punta a punta con fuentes falsas: el job guarda la foto diaria, una fuente que falla
 * mantiene el último valor y registra el error, y los endpoints /hoy e /historico.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MercadoControllerTest {

    private static final LocalDate DIA_1 = LocalDate.of(2026, 10, 27);
    private static final LocalDate DIA_2 = DIA_1.plusDays(1);
    private static final LocalDate DIA_3 = DIA_1.plusDays(2);

    /** Fuente controlada por el test: devuelve lo que se le indique o falla. */
    static class FuenteFalsa implements FuenteCotizacion {
        private final String nombre;
        private final Set<Producto> productos;
        private Supplier<List<Lectura>> respuesta = List::of;

        FuenteFalsa(String nombre, Set<Producto> productos) {
            this.nombre = nombre;
            this.productos = productos;
        }

        void responder(Lectura... lecturas) {
            respuesta = () -> List.of(lecturas);
        }

        void fallar(String mensaje) {
            respuesta = () -> {
                throw new IllegalStateException(mensaje);
            };
        }

        @Override
        public String nombre() {
            return nombre;
        }

        @Override
        public Set<Producto> productos() {
            return productos;
        }

        @Override
        public List<Lectura> leer() {
            return respuesta.get();
        }
    }

    @TestConfiguration
    static class Fuentes {
        @Bean
        FuenteFalsa hacienda() {
            return new FuenteFalsa("Hacienda de prueba", EnumSet.of(Producto.TERNERO, Producto.NOVILLO));
        }

        @Bean
        FuenteFalsa dolar() {
            return new FuenteFalsa("Dólar de prueba", EnumSet.of(Producto.DOLAR_OFICIAL, Producto.DOLAR_BLUE));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ActualizacionCotizacionesJob job;

    @Autowired
    @Qualifier("hacienda")
    private FuenteFalsa hacienda;

    @Autowired
    @Qualifier("dolar")
    private FuenteFalsa dolar;

    private ApiTestClient api;
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        hacienda.responder(ternero("6000", DIA_1), novillo("4000", DIA_1));
        dolar.responder(Lectura.ok(Producto.DOLAR_OFICIAL, new BigDecimal("1455.50"), DIA_1, null),
            Lectura.ok(Producto.DOLAR_BLUE, new BigDecimal("1480"), DIA_1, null));
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- /hoy ----------

    @Test
    void sinCotizacionesDevuelveTodosLosProductosSinDato() throws Exception {
        hoy()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fecha").doesNotExist())
            .andExpect(jsonPath("$.cotizaciones.length()").value(Producto.values().length))
            .andExpect(jsonPath("$.cotizaciones[0].producto").value("TERNERO"))
            .andExpect(jsonPath("$.cotizaciones[0].grupo").value("INVERNADA"))
            .andExpect(jsonPath("$.cotizaciones[0].unidad").value("$/kg vivo"))
            .andExpect(jsonPath("$.cotizaciones[0].estado").value("SIN_DATO"))
            .andExpect(jsonPath("$.cotizaciones[0].valor").doesNotExist())
            .andExpect(jsonPath("$.relacionReposicion").doesNotExist());
    }

    @Test
    void guardaLaFotoDelDiaYCalculaLaRelacionDeReposicion() throws Exception {
        assertThat(job.ejecutar(DIA_1)).isZero();

        hoy()
            .andExpect(jsonPath("$.fecha").value(DIA_1.toString()))
            .andExpect(cotizacion("TERNERO", "valor").value(6000.0))
            .andExpect(cotizacion("TERNERO", "estado").value("ACTUAL"))
            .andExpect(cotizacion("TERNERO", "fuente").value("Hacienda de prueba"))
            .andExpect(cotizacion("TERNERO", "fechaDato").value(DIA_1.toString()))
            .andExpect(cotizacion("DOLAR_OFICIAL", "valor").value(1455.5))
            .andExpect(cotizacion("DOLAR_OFICIAL", "unidad").value("$/US$"))
            // Producto sin ninguna fuente
            .andExpect(cotizacion("SOJA", "estado").value("SIN_DATO"))
            .andExpect(jsonPath("$.relacionReposicion.valor").value(1.5))
            .andExpect(jsonPath("$.relacionReposicion.ternero").value(6000.0))
            .andExpect(jsonPath("$.relacionReposicion.novillo").value(4000.0))
            .andExpect(jsonPath("$.relacionReposicion.actualizada").value(true));
    }

    @Test
    void laRelacionRedondeaADosDecimales() throws Exception {
        hacienda.responder(ternero("5758.85", DIA_1), novillo("4095.65", DIA_1));
        job.ejecutar(DIA_1);

        // 5758,85 / 4095,65 = 1,4061...
        hoy().andExpect(jsonPath("$.relacionReposicion.valor").value(1.41));
    }

    // ---------- Fallas ----------

    @Test
    void siLaFuenteFallaSeMantieneElUltimoValorYSeRegistraElError() throws Exception {
        job.ejecutar(DIA_1);
        hacienda.fallar("Connection refused");

        assertThat(job.ejecutar(DIA_2)).isEqualTo(1);

        hoy()
            .andExpect(jsonPath("$.fecha").value(DIA_2.toString()))
            .andExpect(cotizacion("TERNERO", "valor").value(6000.0))
            .andExpect(cotizacion("TERNERO", "estado").value("ANTERIOR"))
            .andExpect(cotizacion("TERNERO", "fecha").value(DIA_2.toString()))
            // La fecha del dato sigue siendo la del último valor leído
            .andExpect(cotizacion("TERNERO", "fechaDato").value(DIA_1.toString()))
            .andExpect(cotizacion("TERNERO", "error").doesNotExist())
            // La otra fuente se guardó igual
            .andExpect(cotizacion("DOLAR_BLUE", "estado").value("ACTUAL"))
            .andExpect(jsonPath("$.relacionReposicion.valor").value(1.5))
            .andExpect(jsonPath("$.relacionReposicion.actualizada").value(false));

        assertThat(error(Producto.TERNERO, DIA_2)).contains("Connection refused");
        assertThat(error(Producto.TERNERO, DIA_1)).isNull();
    }

    @Test
    void siFallaVariosDiasSigueArrastrandoElUltimoValor() throws Exception {
        job.ejecutar(DIA_1);
        hacienda.fallar("caída");
        job.ejecutar(DIA_2);
        job.ejecutar(DIA_3);

        hoy()
            .andExpect(cotizacion("NOVILLO", "valor").value(4000.0))
            .andExpect(cotizacion("NOVILLO", "estado").value("ANTERIOR"))
            .andExpect(cotizacion("NOVILLO", "fechaDato").value(DIA_1.toString()));
    }

    @Test
    void siFallaSinValoresAnterioresQuedaSinDatoConElError() throws Exception {
        hacienda.fallar("Timeout");

        job.ejecutar(DIA_1);

        hoy()
            .andExpect(cotizacion("TERNERO", "estado").value("SIN_DATO"))
            .andExpect(cotizacion("TERNERO", "valor").doesNotExist())
            .andExpect(jsonPath("$.relacionReposicion").doesNotExist());
        assertThat(error(Producto.TERNERO, DIA_1)).contains("Timeout");
    }

    @Test
    void unProductoQueFallaOFaltaMantieneSuValorYLosDemasSeActualizan() throws Exception {
        job.ejecutar(DIA_1);
        // Novillo con error puntual; el blue directamente no viene en la respuesta
        hacienda.responder(ternero("6600", DIA_2), Lectura.fallo(Producto.NOVILLO, "sin operaciones"));
        dolar.responder(Lectura.ok(Producto.DOLAR_OFICIAL, new BigDecimal("1460"), DIA_2, null));

        job.ejecutar(DIA_2);

        hoy()
            .andExpect(cotizacion("TERNERO", "valor").value(6600.0))
            .andExpect(cotizacion("TERNERO", "estado").value("ACTUAL"))
            .andExpect(cotizacion("NOVILLO", "valor").value(4000.0))
            .andExpect(cotizacion("NOVILLO", "estado").value("ANTERIOR"))
            .andExpect(cotizacion("DOLAR_BLUE", "estado").value("ANTERIOR"))
            .andExpect(jsonPath("$.relacionReposicion.valor").value(1.65));
        assertThat(error(Producto.NOVILLO, DIA_2)).isEqualTo("sin operaciones");
        assertThat(error(Producto.DOLAR_BLUE, DIA_2)).contains("no informó");
    }

    @Test
    void correrDosVecesElMismoDiaActualizaSinDuplicarYUnReintentoFallidoNoPisaLoLeido() throws Exception {
        job.ejecutar(DIA_1);
        hacienda.responder(ternero("6100", DIA_1), novillo("4000", DIA_1));
        job.ejecutar(DIA_1);
        hacienda.fallar("caída");
        job.ejecutar(DIA_1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cotizacion WHERE producto = 'TERNERO'", Integer.class))
            .isEqualTo(1);
        hoy()
            .andExpect(cotizacion("TERNERO", "valor").value(6100.0))
            .andExpect(cotizacion("TERNERO", "estado").value("ACTUAL"));
    }

    // ---------- /historico ----------

    @Test
    void historicoDevuelveLasSeriesPedidasYLaRelacionDeCadaDia() throws Exception {
        job.ejecutar(DIA_1);
        hacienda.responder(ternero("6300", DIA_2), novillo("4200", DIA_2));
        job.ejecutar(DIA_2);
        hacienda.fallar("caída");
        job.ejecutar(DIA_3);

        api.get(token, "/api/mercado/historico?producto=NOVILLO&desde=" + DIA_1 + "&hasta=" + DIA_3)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.desde").value(DIA_1.toString()))
            .andExpect(jsonPath("$.hasta").value(DIA_3.toString()))
            .andExpect(jsonPath("$.series.length()").value(1))
            .andExpect(jsonPath("$.series.NOVILLO.length()").value(3))
            .andExpect(jsonPath("$.series.NOVILLO[0].fecha").value(DIA_1.toString()))
            .andExpect(jsonPath("$.series.NOVILLO[1].valor").value(4200.0))
            .andExpect(jsonPath("$.series.NOVILLO[2].estado").value("ANTERIOR"))
            .andExpect(jsonPath("$.series.NOVILLO[2].fechaDato").value(DIA_2.toString()))
            // La relación sale aunque no se haya pedido el ternero
            .andExpect(jsonPath("$.relacionReposicion.length()").value(3))
            .andExpect(jsonPath("$.relacionReposicion[0].valor").value(1.5))
            .andExpect(jsonPath("$.relacionReposicion[2].fecha").value(DIA_3.toString()));
    }

    @Test
    void historicoSinFiltrosTraeTodosLosProductos() throws Exception {
        job.ejecutar(DIA_1);

        api.get(token, "/api/mercado/historico?desde=" + DIA_1 + "&hasta=" + DIA_1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.series.length()").value(Producto.values().length))
            .andExpect(jsonPath("$.series.TERNERO.length()").value(1))
            .andExpect(jsonPath("$.series.SOJA.length()").value(0));
    }

    @Test
    void historicoValidaElRangoYElProducto() throws Exception {
        api.get(token, "/api/mercado/historico?desde=2026-10-10&hasta=2026-10-01")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("desde"));
        api.get(token, "/api/mercado/historico?desde=2024-01-01&hasta=2026-01-01")
            .andExpect(status().isBadRequest());
        api.get(token, "/api/mercado/historico?producto=CABRA").andExpect(status().isBadRequest());
        api.get(token, "/api/mercado/historico?desde=no-es-fecha").andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        api.get(null, "/api/mercado/hoy").andExpect(status().isUnauthorized());
        api.get(null, "/api/mercado/historico").andExpect(status().isUnauthorized());
    }

    // ---------- Helpers ----------

    private ResultActions hoy() throws Exception {
        return api.get(token, "/api/mercado/hoy");
    }

    /** jsonPath de un campo de la cotización de un producto en /hoy. */
    private static org.springframework.test.web.servlet.result.JsonPathResultMatchers cotizacion(
            String producto, String campo) {
        // Con un solo resultado, Spring compara directamente contra ese valor
        return jsonPath("$.cotizaciones[?(@.producto == '" + producto + "')]." + campo);
    }

    private String error(Producto producto, LocalDate fecha) {
        List<String> errores = new ArrayList<>(jdbc.queryForList(
            "SELECT error FROM cotizacion WHERE producto = ? AND fecha = ?", String.class, producto.name(), fecha));
        return errores.isEmpty() ? null : errores.get(0);
    }

    private static Lectura ternero(String valor, LocalDate fecha) {
        return Lectura.ok(Producto.TERNERO, new BigDecimal(valor), fecha, null);
    }

    private static Lectura novillo(String valor, LocalDate fecha) {
        return Lectura.ok(Producto.NOVILLO, new BigDecimal(valor), fecha, null);
    }
}
