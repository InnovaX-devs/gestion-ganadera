package com.innovax.ganadera.evento.tipos;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #22: evento Movimiento (cambio de lote, rodeo o establecimiento). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MovimientoTest {

    /** El "hoy" de la aplicación es el de Argentina (ver CategoriaConfig), no el del servidor. */
    private static final LocalDate HOY = LocalDate.now(ZoneId.of(CategoriaConfig.ZONA_HORARIA));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private String token;
    /** "La Esperanza": el campo de origen de casi todos los tests. */
    private String origen;
    /** "El Ombú": otro campo de la misma organización, del mismo dueño. */
    private String destino;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        origen = crearEstablecimiento(token, "La Esperanza");
        destino = crearEstablecimiento(token, "El Ombú");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Dentro del mismo establecimiento ----------

    @Test
    void cambiaElLoteDeVariosAnimalesYQuedaEnElHistorialDeCada() throws Exception {
        String recria = crearAgrupacion(origen, "lotes", "Recría");
        String engorde = crearAgrupacion(origen, "lotes", "Engorde");
        String a1 = crearAnimal(origen, "V-1", recria, null);
        String a2 = crearAnimal(origen, "V-2", recria, null);
        String sinTocar = crearAnimal(origen, "V-3", recria, null);

        api.post(token, eventos(origen), movimiento(List.of(a1, a2), Map.of("loteDestinoId", engorde)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2))
            .andExpect(jsonPath("$.eventos[*].tipo", everyItem(is("MOVIMIENTO"))))
            .andExpect(jsonPath("$.eventos[*].datos.desde.lote.nombre", everyItem(is("Recría"))))
            .andExpect(jsonPath("$.eventos[*].datos.hacia.lote.nombre", everyItem(is("Engorde"))));

        for (String id : List.of(a1, a2)) {
            api.get(token, animales(origen) + "/" + id)
                .andExpect(jsonPath("$.lote.nombre").value("Engorde"));
            api.get(token, eventos(origen) + "?animalId=" + id)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].tipo").value("MOVIMIENTO"))
                .andExpect(jsonPath("$.contenido[0].datos.desde.lote.nombre").value("Recría"))
                .andExpect(jsonPath("$.contenido[0].datos.hacia.lote.nombre").value("Engorde"));
        }
        api.get(token, animales(origen) + "/" + sinTocar)
            .andExpect(jsonPath("$.lote.nombre").value("Recría"));
        api.get(token, eventos(origen) + "?animalId=" + sinTocar).andExpect(jsonPath("$.total").value(0));
        api.get(token, url(origen, "lotes"))
            .andExpect(jsonPath("$[?(@.nombre=='Recría')].cantidadAnimales").value(1))
            .andExpect(jsonPath("$[?(@.nombre=='Engorde')].cantidadAnimales").value(2));
    }

    @Test
    void cambiarElRodeoNoTocaElLoteYSePuedeQuitarCadaUno() throws Exception {
        String lote = crearAgrupacion(origen, "lotes", "Recría");
        String cria = crearAgrupacion(origen, "rodeos", "Cría");
        String invernada = crearAgrupacion(origen, "rodeos", "Invernada");
        String vaca = crearAnimal(origen, "V-1", lote, cria);

        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("rodeoDestinoId", invernada)))
            .andExpect(status().isCreated());
        api.get(token, animales(origen) + "/" + vaca)
            .andExpect(jsonPath("$.lote.nombre").value("Recría"))
            .andExpect(jsonPath("$.rodeo.nombre").value("Invernada"));

        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("quitarLote", true)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].datos.hacia.lote").doesNotExist())
            .andExpect(jsonPath("$.eventos[0].datos.hacia.rodeo.nombre").value("Invernada"));
        api.get(token, animales(origen) + "/" + vaca)
            .andExpect(jsonPath("$.lote").doesNotExist())
            .andExpect(jsonPath("$.rodeo.nombre").value("Invernada"));
    }

    @Test
    void sePuedeMoverTodoUnLoteDeUnaVez() throws Exception {
        String recria = crearAgrupacion(origen, "lotes", "Recría");
        String cria = crearAgrupacion(origen, "rodeos", "Cría");
        String a1 = crearAnimal(origen, "V-1", recria, null);
        String a2 = crearAnimal(origen, "V-2", recria, null);
        String afuera = crearAnimal(origen, "V-3", null, null);

        var body = movimiento(null, Map.of("rodeoDestinoId", cria));
        body.put("loteId", recria);
        api.post(token, eventos(origen), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2));

        api.get(token, animales(origen) + "/" + a1).andExpect(jsonPath("$.rodeo.nombre").value("Cría"));
        api.get(token, animales(origen) + "/" + a2).andExpect(jsonPath("$.rodeo.nombre").value("Cría"));
        api.get(token, animales(origen) + "/" + afuera).andExpect(jsonPath("$.rodeo").doesNotExist());
    }

    // ---------- Entre establecimientos ----------

    @Test
    void entreEstablecimientosActualizaLaUbicacionYElHistorialLoSigue() throws Exception {
        String loteOrigen = crearAgrupacion(origen, "lotes", "Recría");
        String rodeoOrigen = crearAgrupacion(origen, "rodeos", "Cría");
        String loteDestino = crearAgrupacion(destino, "lotes", "Potrero 1");
        String vaca = crearAnimal(origen, "V-1", loteOrigen, rodeoOrigen);
        String otra = crearAnimal(origen, "V-2", null, null);
        api.post(token, eventos(origen), observacion(vaca, "Cojea de la pata trasera")).andExpect(status().isCreated());

        api.post(token, eventos(origen), movimiento(List.of(vaca, otra),
                Map.of("establecimientoDestinoId", destino, "loteDestinoId", loteDestino)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2))
            .andExpect(jsonPath("$.eventos[*].datos.hacia.establecimiento.nombre", everyItem(is("El Ombú"))));

        // La ubicación actual es el campo de destino, con el lote pedido y sin el rodeo de origen
        api.get(token, animales(destino) + "/" + vaca)
            .andExpect(jsonPath("$.establecimientoId").value(destino))
            .andExpect(jsonPath("$.lote.nombre").value("Potrero 1"))
            .andExpect(jsonPath("$.rodeo").doesNotExist());
        api.get(token, animales(origen) + "/" + vaca).andExpect(status().isNotFound());
        api.get(token, animales(origen)).andExpect(jsonPath("$.total").value(0));
        api.get(token, animales(destino)).andExpect(jsonPath("$.total").value(2));
        api.get(token, url(origen, "lotes")).andExpect(jsonPath("$[0].cantidadAnimales").value(0));
        api.get(token, url(destino, "lotes")).andExpect(jsonPath("$[0].cantidadAnimales").value(2));

        // Y queda en el historial, con de dónde a dónde
        api.get(token, eventos(destino) + "?animalId=" + vaca + "&tipo=MOVIMIENTO")
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].establecimiento.nombre").value("La Esperanza"))
            .andExpect(jsonPath("$.contenido[0].datos.desde.establecimiento.nombre").value("La Esperanza"))
            .andExpect(jsonPath("$.contenido[0].datos.desde.lote.nombre").value("Recría"))
            .andExpect(jsonPath("$.contenido[0].datos.desde.rodeo.nombre").value("Cría"))
            .andExpect(jsonPath("$.contenido[0].datos.hacia.establecimiento.nombre").value("El Ombú"))
            .andExpect(jsonPath("$.contenido[0].datos.hacia.lote.nombre").value("Potrero 1"));
    }

    @Test
    void elHistorialDelAnimalIncluyeLoRegistradoEnLosCamposPorLosQuePaso() throws Exception {
        String vaca = crearAnimal(origen, "V-1", null, null);
        api.post(token, eventos(origen), observacion(vaca, "Antes de la mudanza")).andExpect(status().isCreated());
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("establecimientoDestinoId", destino)))
            .andExpect(status().isCreated());
        api.post(token, eventos(destino), observacion(vaca, "Ya en el nuevo campo")).andExpect(status().isCreated());

        // Desde el campo actual se ve todo el recorrido; el campo de origen solo ve lo que cargó él
        api.get(token, eventos(destino) + "?animalId=" + vaca).andExpect(jsonPath("$.total").value(3));
        api.get(token, eventos(origen) + "?animalId=" + vaca).andExpect(jsonPath("$.total").value(2));
        // Sin filtrar por animal, cada campo lista solo lo suyo
        api.get(token, eventos(destino)).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void laCarenciaYElPesoSiguenAlAnimalAOtroCampo() throws Exception {
        String vaca = crearAnimal(origen, "V-1", null, null);
        String producto = api.leer(api.post(token, url(origen, "productos-sanitarios"),
            Map.of("nombre", "Ivomec", "tipo", "ANTIPARASITARIO", "diasCarencia", 28))
            .andExpect(status().isCreated()), "id");
        var tratamiento = new HashMap<String, Object>();
        tratamiento.put("tipo", "TRATAMIENTO");
        tratamiento.put("fecha", HOY.toString());
        tratamiento.put("animalIds", List.of(vaca));
        tratamiento.put("datos", Map.of("productoId", producto, "dosis", "5 ml", "via", "SUBCUTANEA"));
        api.post(token, eventos(origen), tratamiento).andExpect(status().isCreated());
        api.post(token, eventos(origen), pesaje(vaca, HOY.minusDays(10), 300.5)).andExpect(status().isCreated());

        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("establecimientoDestinoId", destino)))
            .andExpect(status().isCreated());
        api.post(token, eventos(destino), pesaje(vaca, HOY, 320.5)).andExpect(status().isCreated());

        // Sin esto, mover un animal en carencia le "borraría" la carencia
        api.get(token, animales(destino) + "/" + vaca)
            .andExpect(jsonPath("$.carencia.tratamientos[0].producto").value("Ivomec"));
        // La curva de peso sigue entera y la ganancia se calcula contra el pesaje del campo anterior
        api.get(token, url(destino, "pesajes") + "?animalId=" + vaca)
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.contenido[0].kg").value(320.5))
            .andExpect(jsonPath("$.contenido[0].diasDesdeAnterior").value(10));
    }

    @Test
    void noSePuedeMoverUnAnimalSiLaCaravanaYaEstaEnElDestino() throws Exception {
        String vaca = crearAnimal(origen, "V-1", null, null);
        String otra = crearAnimal(origen, "V-2", null, null);
        crearAnimal(destino, "V-1", null, null);

        api.post(token, eventos(origen), movimiento(List.of(vaca, otra), Map.of("establecimientoDestinoId", destino)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya hay un animal con la caravana V-1 en El Ombú"));

        // Todo o nada: ninguno se movió
        api.get(token, animales(origen)).andExpect(jsonPath("$.total").value(2));
        api.get(token, eventos(origen)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void elLoteDelDestinoTieneQueSerDelDestino() throws Exception {
        String loteOrigen = crearAgrupacion(origen, "lotes", "Recría");
        String vaca = crearAnimal(origen, "V-1", loteOrigen, null);

        api.post(token, eventos(origen), movimiento(List.of(vaca),
                Map.of("establecimientoDestinoId", destino, "loteDestinoId", loteOrigen)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El lote elegido no existe en el establecimiento de destino"));
        api.get(token, animales(origen) + "/" + vaca).andExpect(status().isOk());
    }

    // ---------- Organización y permisos ----------

    @Test
    void noSePuedeMoverAUnEstablecimientoDeOtraOrganizacion() throws Exception {
        String vaca = crearAnimal(origen, "V-1", null, null);
        String tokenB = api.registrarCuenta("Organización B");
        String ajeno = crearEstablecimiento(tokenB, "Campo ajeno");

        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("establecimientoDestinoId", ajeno)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El establecimiento de destino no existe"));

        api.get(token, animales(origen) + "/" + vaca).andExpect(status().isOk());
        api.get(tokenB, animales(ajeno)).andExpect(jsonPath("$.total").value(0));
        // Ni los animales ni los eventos de una organización los puede tocar la otra
        api.post(tokenB, eventos(origen), movimiento(List.of(vaca), Map.of("quitarLote", true)))
            .andExpect(status().isNotFound());
    }

    @Test
    void sinSerMiembroDelDestinoNoSePuedeMoverAhi() throws Exception {
        String vaca = crearAnimal(origen, "V-1", null, null);
        String email = crearUsuario();
        agregarMiembro(origen, email, Rol.ENCARGADO);
        String tokenEncargado = login(email);

        api.post(tokenEncargado, eventos(origen), movimiento(List.of(vaca), Map.of("establecimientoDestinoId", destino)))
            .andExpect(status().isNotFound());
        api.get(token, animales(origen) + "/" + vaca).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 403", "SOCIO, 403"})
    void paraMoverAOtroCampoHaceFaltaPoderGestionarAnimalesAllaTambien(Rol rolEnDestino, int esperado) throws Exception {
        String vaca = crearAnimal(origen, "V-1", null, null);
        String email = crearUsuario();
        agregarMiembro(origen, email, Rol.ENCARGADO);
        agregarMiembro(destino, email, rolEnDestino);

        api.post(login(email), eventos(origen),
                movimiento(List.of(vaca), Map.of("establecimientoDestinoId", destino)))
            .andExpect(status().is(esperado));
    }

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 403", "SOCIO, 403"})
    void soloQuienGestionaAnimalesPuedeMoverlosDeLote(Rol rol, int esperado) throws Exception {
        String lote = crearAgrupacion(origen, "lotes", "Recría");
        String vaca = crearAnimal(origen, "V-1", null, null);
        String email = crearUsuario();
        agregarMiembro(origen, email, rol);

        api.post(login(email), eventos(origen), movimiento(List.of(vaca), Map.of("loteDestinoId", lote)))
            .andExpect(status().is(esperado));
    }

    // ---------- Validaciones ----------

    @Test
    void validaQueSeIndiqueUnDestinoCoherente() throws Exception {
        String lote = crearAgrupacion(origen, "lotes", "Recría");
        String vaca = crearAnimal(origen, "V-1", null, null);
        String ajeno = crearAgrupacion(destino, "lotes", "Ajeno");

        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Indicá a dónde se mueven los animales"));
        // Mandar el mismo establecimiento y nada más tampoco cambia nada
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("establecimientoDestinoId", origen)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Indicá a dónde se mueven los animales"));
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("loteDestinoId", lote, "quitarLote", true)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Elegí un lote o quitalo, no las dos cosas"));
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("loteDestinoId", ajeno)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El lote elegido no existe en este establecimiento"));
        api.post(token, eventos(origen), movimiento(List.of(), Map.of("loteDestinoId", lote)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Elegí al menos un animal"));

        api.get(token, eventos(origen)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void siAlgunAnimalYaEstaEnElDestinoNoSeMueveNinguno() throws Exception {
        String recria = crearAgrupacion(origen, "lotes", "Recría");
        String engorde = crearAgrupacion(origen, "lotes", "Engorde");
        String yaAhi = crearAnimal(origen, "V-1", engorde, null);
        String otra = crearAnimal(origen, "V-2", recria, null);

        api.post(token, eventos(origen), movimiento(List.of(otra, yaAhi), Map.of("loteDestinoId", engorde)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("V-1 ya está en el destino elegido"));

        api.get(token, animales(origen) + "/" + otra).andExpect(jsonPath("$.lote.nombre").value("Recría"));
        api.get(token, eventos(origen)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void losAnimalesTienenQueSerActivosYDelEstablecimiento() throws Exception {
        String lote = crearAgrupacion(origen, "lotes", "Recría");
        String deOtroCampo = crearAnimal(destino, "Z-1", null, null);
        String vendida = crearAnimal(origen, "V-9", null, null);
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE id = ?", UUID.fromString(vendida));

        for (String id : List.of(deOtroCampo, vendida)) {
            api.post(token, eventos(origen), movimiento(List.of(id), Map.of("loteDestinoId", lote)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Alguno de los animales elegidos no está en este establecimiento"));
        }
    }

    // ---------- Anulación ----------

    @Test
    void anularUnMovimientoDeLoteDevuelveElAnimalAlLoteAnterior() throws Exception {
        String recria = crearAgrupacion(origen, "lotes", "Recría");
        String engorde = crearAgrupacion(origen, "lotes", "Engorde");
        String vaca = crearAnimal(origen, "V-1", recria, null);
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("loteDestinoId", engorde)))
            .andExpect(status().isCreated());

        api.delete(token, eventos(origen) + "/" + idDelUltimoMovimiento(origen, vaca)).andExpect(status().isNoContent());

        api.get(token, animales(origen) + "/" + vaca).andExpect(jsonPath("$.lote.nombre").value("Recría"));
        api.get(token, eventos(origen) + "?animalId=" + vaca).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anularUnMovimientoEntreEstablecimientosDevuelveElAnimalAlOrigen() throws Exception {
        String loteOrigen = crearAgrupacion(origen, "lotes", "Recría");
        String rodeoOrigen = crearAgrupacion(origen, "rodeos", "Cría");
        String vaca = crearAnimal(origen, "V-1", loteOrigen, rodeoOrigen);
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("establecimientoDestinoId", destino)))
            .andExpect(status().isCreated());
        String eventoId = idDelUltimoMovimiento(destino, vaca);

        // Se anula desde el campo donde se registró
        api.delete(token, eventos(origen) + "/" + eventoId).andExpect(status().isNoContent());

        api.get(token, animales(origen) + "/" + vaca)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.establecimientoId").value(origen))
            .andExpect(jsonPath("$.lote.nombre").value("Recría"))
            .andExpect(jsonPath("$.rodeo.nombre").value("Cría"));
        api.get(token, animales(destino) + "/" + vaca).andExpect(status().isNotFound());
    }

    @Test
    void unMovimientoSoloSeAnulaSiElAnimalSigueDondeLoDejo() throws Exception {
        String l1 = crearAgrupacion(origen, "lotes", "Uno");
        String l2 = crearAgrupacion(origen, "lotes", "Dos");
        String l3 = crearAgrupacion(origen, "lotes", "Tres");
        String vaca = crearAnimal(origen, "V-1", l1, null);
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("loteDestinoId", l2))).andExpect(status().isCreated());
        String primero = idDelUltimoMovimiento(origen, vaca);
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("loteDestinoId", l3))).andExpect(status().isCreated());

        api.delete(token, eventos(origen) + "/" + primero)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message")
                .value("No se puede anular el movimiento: V-1 se movió después. Anulá primero su último movimiento."));
        api.get(token, animales(origen) + "/" + vaca).andExpect(jsonPath("$.lote.nombre").value("Tres"));

        // De atrás para adelante sí se puede
        api.delete(token, eventos(origen) + "/" + idDelUltimoMovimiento(origen, vaca)).andExpect(status().isNoContent());
        api.get(token, animales(origen) + "/" + vaca).andExpect(jsonPath("$.lote.nombre").value("Dos"));
        api.delete(token, eventos(origen) + "/" + primero).andExpect(status().isNoContent());
        api.get(token, animales(origen) + "/" + vaca).andExpect(jsonPath("$.lote.nombre").value("Uno"));
    }

    @Test
    void unMovimientoDeUnAnimalVendidoNoSeAnula() throws Exception {
        String lote = crearAgrupacion(origen, "lotes", "Recría");
        String vaca = crearAnimal(origen, "V-1", null, null);
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("loteDestinoId", lote))).andExpect(status().isCreated());
        String eventoId = idDelUltimoMovimiento(origen, vaca);
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE id = ?", UUID.fromString(vaca));

        api.delete(token, eventos(origen) + "/" + eventoId)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message")
                .value("No se puede anular el movimiento: V-1 ya no está en el stock (se vendió o se dio de baja)"));
    }

    @Test
    void nadaSeAnulaSiLaCaravanaDeOrigenYaFueOcupada() throws Exception {
        String vaca = crearAnimal(origen, "V-1", null, null);
        api.post(token, eventos(origen), movimiento(List.of(vaca), Map.of("establecimientoDestinoId", destino)))
            .andExpect(status().isCreated());
        String eventoId = idDelUltimoMovimiento(destino, vaca);
        crearAnimal(origen, "V-1", null, null);

        api.delete(token, eventos(origen) + "/" + eventoId)
            .andExpect(status().isConflict());
        api.get(token, animales(destino) + "/" + vaca).andExpect(status().isOk());
    }

    // ---------- Asignación masiva (animales/asignar) ----------

    @Test
    void asignarLoteATodosLosSeleccionadosTambienQuedaEnElHistorial() throws Exception {
        String recria = crearAgrupacion(origen, "lotes", "Recría");
        String engorde = crearAgrupacion(origen, "lotes", "Engorde");
        String a1 = crearAnimal(origen, "V-1", recria, null);
        String a2 = crearAnimal(origen, "V-2", null, null);
        String yaAhi = crearAnimal(origen, "V-3", engorde, null);

        api.post(token, animales(origen) + "/asignar",
                Map.of("animalIds", List.of(a1, a2, yaAhi), "campo", "LOTE", "destinoId", engorde))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actualizados").value(3));

        api.get(token, eventos(origen) + "?animalId=" + a1)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].tipo").value("MOVIMIENTO"))
            .andExpect(jsonPath("$.contenido[0].fecha").value(HOY.toString()))
            .andExpect(jsonPath("$.contenido[0].datos.desde.lote.nombre").value("Recría"))
            .andExpect(jsonPath("$.contenido[0].datos.hacia.lote.nombre").value("Engorde"));
        api.get(token, eventos(origen) + "?animalId=" + a2)
            .andExpect(jsonPath("$.contenido[0].datos.desde.lote").doesNotExist());
        // El que ya estaba en el lote no cambia, así que no genera evento
        api.get(token, eventos(origen) + "?animalId=" + yaAhi).andExpect(jsonPath("$.total").value(0));

        var quitar = new HashMap<String, Object>();
        quitar.put("animalIds", List.of(a1));
        quitar.put("campo", "LOTE");
        quitar.put("destinoId", null);
        api.post(token, animales(origen) + "/asignar", quitar).andExpect(status().isOk());
        api.get(token, animales(origen) + "/" + a1).andExpect(jsonPath("$.lote").doesNotExist());
        api.get(token, eventos(origen) + "?animalId=" + a1).andExpect(jsonPath("$.total").value(2));
    }

    // ---------- Helpers ----------

    private static String url(String establecimiento, String recurso) {
        return "/api/establecimientos/" + establecimiento + "/" + recurso;
    }

    private static String eventos(String establecimiento) {
        return url(establecimiento, "eventos");
    }

    private static String animales(String establecimiento) {
        return url(establecimiento, "animales");
    }

    private static Map<String, Object> movimiento(List<String> animalIds, Map<String, Object> datos) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "MOVIMIENTO");
        body.put("fecha", HOY.toString());
        body.put("animalIds", animalIds);
        body.put("datos", datos);
        return body;
    }

    private static Map<String, Object> observacion(String animalId, String texto) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "OBSERVACION");
        body.put("fecha", HOY.toString());
        body.put("animalIds", List.of(animalId));
        body.put("datos", Map.of("texto", texto));
        return body;
    }

    private static Map<String, Object> pesaje(String animalId, LocalDate fecha, double kg) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "PESAJE");
        body.put("fecha", fecha.toString());
        body.put("animalIds", List.of(animalId));
        body.put("datos", Map.of("kg", kg));
        return body;
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private String crearAgrupacion(String establecimiento, String recurso, String nombre) throws Exception {
        return api.leer(api.post(token, url(establecimiento, recurso), Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private String crearAnimal(String establecimiento, String caravana, String loteId, String rodeoId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", "HEMBRA");
        body.put("categoria", "VACA");
        body.put("loteId", loteId);
        body.put("rodeoId", rodeoId);
        return api.leer(api.post(token, animales(establecimiento), body).andExpect(status().isCreated()), "id");
    }

    /** Id del movimiento más reciente del animal, visto desde ese campo (el listado va del más nuevo al más viejo). */
    private String idDelUltimoMovimiento(String establecimiento, String animalId) throws Exception {
        var json = objectMapper.readTree(api.get(token,
                eventos(establecimiento) + "?tipo=MOVIMIENTO&animalId=" + animalId)
            .andReturn().getResponse().getContentAsString());
        return json.get("contenido").get(0).get("id").asText();
    }

    /** Usuario nuevo de la organización, todavía sin lugar en ningún establecimiento. */
    private String crearUsuario() throws Exception {
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        return email;
    }

    private void agregarMiembro(String establecimiento, String email, Rol rol) throws Exception {
        api.post(token, url(establecimiento, "miembros"), Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
