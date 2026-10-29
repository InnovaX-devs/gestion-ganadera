package com.innovax.ganadera.sanidad;

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
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #20: catálogo de productos sanitarios, tratamientos (individual y por lote/rodeo) y carencia. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TratamientoControllerTest {

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
    private String establecimientoId;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        establecimientoId = crearEstablecimiento(token, "La Esperanza");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Catálogo ----------

    @Test
    void elCatalogoSeArmaEditaYSeDaDeBaja() throws Exception {
        var body = producto("Ivomec", "ANTIPARASITARIO", 28);
        body.put("dosisHabitual", " 1 ml cada 50 kg ");
        body.put("viaHabitual", "SUBCUTANEA");
        String id = api.leer(api.post(token, productos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("Ivomec"))
            .andExpect(jsonPath("$.tipo").value("ANTIPARASITARIO"))
            .andExpect(jsonPath("$.diasCarencia").value(28))
            .andExpect(jsonPath("$.dosisHabitual").value("1 ml cada 50 kg"))
            .andExpect(jsonPath("$.viaHabitual").value("SUBCUTANEA")), "id");
        crearProducto("Aftosa", "VACUNA", 0);

        api.get(token, productos()).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].nombre").value("Aftosa"))
            .andExpect(jsonPath("$[1].nombre").value("Ivomec"));

        api.put(token, productos() + "/" + id, producto("Ivomec Gold", "ANTIPARASITARIO", 35))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Ivomec Gold"))
            .andExpect(jsonPath("$.diasCarencia").value(35))
            .andExpect(jsonPath("$.dosisHabitual").doesNotExist());

        api.delete(token, productos() + "/" + id).andExpect(status().isNoContent());
        api.get(token, productos()).andExpect(jsonPath("$.length()").value(1));
        api.delete(token, productos() + "/" + id).andExpect(status().isNotFound());
    }

    @Test
    void elCatalogoValidaYNoPermiteNombresRepetidos() throws Exception {
        api.post(token, productos(), producto("  ", "VACUNA", 0))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"));
        api.post(token, productos(), producto("X", "VACUNA", -1)).andExpect(status().isBadRequest());
        api.post(token, productos(), producto("X", "VACUNA", 366)).andExpect(status().isBadRequest());
        api.post(token, productos(), producto("X", "INVENTADO", 1)).andExpect(status().isBadRequest());

        crearProducto("Aftosa", "VACUNA", 0);
        api.post(token, productos(), producto("aftosa", "VACUNA", 0)).andExpect(status().isConflict());
        // El mismo nombre sí se puede usar en otro establecimiento
        api.post(token, productos(crearEstablecimiento(token, "El Ombú")), producto("Aftosa", "VACUNA", 0))
            .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 201", "SOCIO, 403"})
    void elCatalogoLoEditanQuienesRegistranSanidadYTodosLoVen(Rol rol, int esperado) throws Exception {
        String tokenRol = tokenConRol(rol);

        api.post(tokenRol, productos(), producto("Aftosa", "VACUNA", 0)).andExpect(status().is(esperado));
        api.get(tokenRol, productos()).andExpect(status().isOk());
    }

    @Test
    void elCatalogoEsDeLaOrganizacion() throws Exception {
        String id = crearProducto("Aftosa", "VACUNA", 0);
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, productos()).andExpect(status().isNotFound());
        api.post(tokenB, productos(), producto("Intruso", "OTRO", 0)).andExpect(status().isNotFound());
        api.delete(tokenB, productos() + "/" + id).andExpect(status().isNotFound());
    }

    // ---------- Registro individual ----------

    @Test
    void registraUnTratamientoConLosDatosDelProducto() throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);

        var body = tratamiento(HOY.minusDays(1), ivomec, List.of(vaca));
        body.put("datos", datos(ivomec, "5 ml", "SUBCUTANEA", " Dra. Pérez ", null));
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(1))
            .andExpect(jsonPath("$.eventos[0].tipo").value("TRATAMIENTO"))
            .andExpect(jsonPath("$.eventos[0].animal.nombre").value("V-1"))
            .andExpect(jsonPath("$.eventos[0].datos.productoId").value(ivomec))
            .andExpect(jsonPath("$.eventos[0].datos.producto").value("Ivomec"))
            .andExpect(jsonPath("$.eventos[0].datos.tipo").value("ANTIPARASITARIO"))
            .andExpect(jsonPath("$.eventos[0].datos.dosis").value("5 ml"))
            .andExpect(jsonPath("$.eventos[0].datos.via").value("SUBCUTANEA"))
            .andExpect(jsonPath("$.eventos[0].datos.veterinario").value("Dra. Pérez"))
            .andExpect(jsonPath("$.eventos[0].datos.diasCarencia").value(28));

        api.get(token, eventos() + "?tipo=TRATAMIENTO&animalId=" + vaca)
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void laCarenciaIndicadaPisaLaDelProductoYElServidorIgnoraNombreYTipoEnviados() throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);

        var body = tratamiento(HOY, ivomec, List.of(vaca));
        var datos = datos(ivomec, "5 ml", "ORAL", null, 7);
        datos.put("producto", "Nombre inventado");
        datos.put("tipo", "VACUNA");
        body.put("datos", datos);
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].datos.producto").value("Ivomec"))
            .andExpect(jsonPath("$.eventos[0].datos.tipo").value("ANTIPARASITARIO"))
            .andExpect(jsonPath("$.eventos[0].datos.diasCarencia").value(7))
            .andExpect(jsonPath("$.eventos[0].datos.veterinario").doesNotExist());
    }

    @Test
    void elHistorialConservaElNombreAunqueSeRenombreElProducto() throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);
        api.post(token, eventos(), tratamiento(HOY, ivomec, List.of(vaca))).andExpect(status().isCreated());

        api.put(token, productos() + "/" + ivomec, producto("Otro nombre", "OTRO", 0)).andExpect(status().isOk());

        api.get(token, eventos() + "?animalId=" + vaca)
            .andExpect(jsonPath("$.contenido[0].datos.producto").value("Ivomec"))
            .andExpect(jsonPath("$.contenido[0].datos.diasCarencia").value(28));
    }

    // ---------- Validaciones ----------

    @Test
    void validaLosDatosDelTratamiento() throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);

        var sinNada = tratamiento(HOY, ivomec, List.of(vaca));
        sinNada.put("datos", Map.of());
        api.post(token, eventos(), sinNada)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='datos.productoId')].message").value("Elegí el producto"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='datos.dosis')].message").value("Indicá la dosis"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='datos.via')].message")
                .value("Elegí la vía de aplicación"));

        var carenciaInvalida = tratamiento(HOY, ivomec, List.of(vaca));
        carenciaInvalida.put("datos", datos(ivomec, "5 ml", "ORAL", null, 400));
        api.post(token, eventos(), carenciaInvalida)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.diasCarencia"));

        var viaInventada = tratamiento(HOY, ivomec, List.of(vaca));
        viaInventada.put("datos", datos(ivomec, "5 ml", "POR_LA_OREJA", null, null));
        api.post(token, eventos(), viaInventada).andExpect(status().isBadRequest());

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void elProductoTieneQueSerActivoYDeEsteEstablecimiento() throws Exception {
        String vaca = crearAnimal("V-1");
        String otro = crearEstablecimiento(token, "El Ombú");
        String ajeno = api.leer(api.post(token, productos(otro), producto("Ajeno", "OTRO", 0)), "id");
        String dadoDeBaja = crearProducto("Viejo", "OTRO", 0);
        api.delete(token, productos() + "/" + dadoDeBaja).andExpect(status().isNoContent());

        for (String productoId : List.of(ajeno, dadoDeBaja, UUID.randomUUID().toString())) {
            api.post(token, eventos(), tratamiento(HOY, productoId, List.of(vaca)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("El producto elegido no existe en este establecimiento"));
        }
    }

    @Test
    void tratamientoSinAnimalesNiGrupoDevuelve422() throws Exception {
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);

        api.post(token, eventos(), tratamiento(HOY, ivomec, List.of()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Elegí al menos un animal"));
    }

    // ---------- Carga por lote y por rodeo ----------

    @Test
    void cargaPorLoteCreaUnTratamientoPorCadaAnimalActivo() throws Exception {
        String loteId = crearAgrupacion("lotes", "Recría");
        String a1 = crearAnimalEn("V-1", loteId, null);
        String a2 = crearAnimalEn("V-2", loteId, null);
        String vendido = crearAnimalEn("V-3", loteId, null);
        crearAnimalEn("V-4", null, null);
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE id = ?", UUID.fromString(vendido));
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);

        var body = tratamiento(HOY, ivomec, null);
        body.put("loteId", loteId);
        String operacion = api.leer(api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2))
            .andExpect(jsonPath("$.eventos[0].lote").doesNotExist())
            .andExpect(jsonPath("$.eventos[0].animal.nombre").value("V-1")), "operacionId");

        api.get(token, eventos() + "?tipo=TRATAMIENTO").andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.contenido[0].operacionId").value(operacion))
            .andExpect(jsonPath("$.contenido[1].operacionId").value(operacion));
        api.get(token, eventos() + "?animalId=" + a1).andExpect(jsonPath("$.total").value(1));
        api.get(token, eventos() + "?animalId=" + a2).andExpect(jsonPath("$.total").value(1));
        api.get(token, eventos() + "?animalId=" + vendido).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void cargaPorRodeoCreaUnTratamientoPorCadaAnimal() throws Exception {
        String rodeoId = crearAgrupacion("rodeos", "Cría");
        crearAnimalEn("V-1", null, rodeoId);
        crearAnimalEn("V-2", null, rodeoId);
        crearAnimalEn("V-3", null, null);
        String aftosa = crearProducto("Aftosa", "VACUNA", 0);

        var body = tratamiento(HOY, aftosa, null);
        body.put("rodeoId", rodeoId);
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2));
        api.get(token, eventos() + "?tipo=TRATAMIENTO").andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void cargaPorGrupoVacioOMezclada422() throws Exception {
        String loteVacio = crearAgrupacion("lotes", "Vacío");
        String rodeo = crearAgrupacion("rodeos", "Cría");
        String vaca = crearAnimalEn("V-1", loteVacio, null);
        jdbc.update("UPDATE animal SET estado = 'MUERTO' WHERE id = ?", UUID.fromString(vaca));
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);

        var deLote = tratamiento(HOY, ivomec, null);
        deLote.put("loteId", loteVacio);
        api.post(token, eventos(), deLote)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El lote Vacío no tiene animales"));

        var loteYRodeo = tratamiento(HOY, ivomec, null);
        loteYRodeo.put("loteId", loteVacio);
        loteYRodeo.put("rodeoId", rodeo);
        api.post(token, eventos(), loteYRodeo)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Indicá un lote o un rodeo, no los dos"));

        String otra = crearAnimal("V-2");
        var animalesYRodeo = tratamiento(HOY, ivomec, List.of(otra));
        animalesYRodeo.put("rodeoId", rodeo);
        api.post(token, eventos(), animalesYRodeo)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Indicá animales o un rodeo, no las dos cosas"));

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void unaObservacionNoSePuedeRegistrarSobreUnRodeo() throws Exception {
        String rodeo = crearAgrupacion("rodeos", "Cría");
        var body = new HashMap<String, Object>();
        body.put("tipo", "OBSERVACION");
        body.put("fecha", HOY.toString());
        body.put("rodeoId", rodeo);
        body.put("datos", Map.of("texto", "X"));

        api.post(token, eventos(), body)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El evento Observación no se puede registrar sobre un rodeo"));
    }

    // ---------- Carencia ----------

    @Test
    void elAnimalMuestraSiEstaEnCarenciaYHastaCuando() throws Exception {
        String vaca = crearAnimal("V-1");
        String sana = crearAnimal("V-2");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);
        api.post(token, eventos(), tratamiento(HOY.minusDays(10), ivomec, List.of(vaca))).andExpect(status().isCreated());

        String libreDesde = HOY.minusDays(10).plusDays(28).toString();
        api.get(token, url("animales") + "/" + vaca)
            .andExpect(jsonPath("$.carencia.libreDesde").value(libreDesde))
            .andExpect(jsonPath("$.carencia.tratamientos[0].producto").value("Ivomec"))
            .andExpect(jsonPath("$.carencia.tratamientos[0].diasCarencia").value(28))
            .andExpect(jsonPath("$.carencia.tratamientos[0].fecha").value(HOY.minusDays(10).toString()));
        api.get(token, url("animales") + "/" + sana).andExpect(jsonPath("$.carencia").doesNotExist());

        api.get(token, url("animales") + "?q=V-")
            .andExpect(jsonPath("$.contenido[0].caravana").value("V-1"))
            .andExpect(jsonPath("$.contenido[0].carencia.libreDesde").value(libreDesde))
            .andExpect(jsonPath("$.contenido[1].caravana").value("V-2"))
            .andExpect(jsonPath("$.contenido[1].carencia").doesNotExist());
    }

    @Test
    void sinCarenciaOConCarenciaVencidaElAnimalEstaLibre() throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);
        String aftosa = crearProducto("Aftosa", "VACUNA", 0);

        // Vencida: aplicada hace 40 días con 28 de carencia
        api.post(token, eventos(), tratamiento(HOY.minusDays(40), ivomec, List.of(vaca))).andExpect(status().isCreated());
        // Sin carencia
        api.post(token, eventos(), tratamiento(HOY, aftosa, List.of(vaca))).andExpect(status().isCreated());
        api.get(token, url("animales") + "/" + vaca).andExpect(jsonPath("$.carencia").doesNotExist());

        // El día que se cumple la carencia ya está libre; el día anterior todavía no
        api.post(token, eventos(), tratamiento(HOY.minusDays(28), ivomec, List.of(vaca))).andExpect(status().isCreated());
        api.get(token, url("animales") + "/" + vaca).andExpect(jsonPath("$.carencia").doesNotExist());
        api.post(token, eventos(), tratamiento(HOY.minusDays(27), ivomec, List.of(vaca))).andExpect(status().isCreated());
        api.get(token, url("animales") + "/" + vaca)
            .andExpect(jsonPath("$.carencia.libreDesde").value(HOY.plusDays(1).toString()));
    }

    @Test
    void conVariosTratamientosManejaLaCarenciaMasLarga() throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);
        String oxi = crearProducto("Oxitetraciclina", "ANTIBIOTICO", 60);
        api.post(token, eventos(), tratamiento(HOY.minusDays(5), ivomec, List.of(vaca))).andExpect(status().isCreated());
        api.post(token, eventos(), tratamiento(HOY.minusDays(2), oxi, List.of(vaca))).andExpect(status().isCreated());

        api.get(token, url("animales") + "/" + vaca)
            .andExpect(jsonPath("$.carencia.libreDesde").value(HOY.minusDays(2).plusDays(60).toString()))
            .andExpect(jsonPath("$.carencia.tratamientos.length()").value(2))
            .andExpect(jsonPath("$.carencia.tratamientos[0].producto").value("Oxitetraciclina"));
    }

    @Test
    void anularElTratamientoLevantaLaCarencia() throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);
        api.post(token, eventos(), tratamiento(HOY, ivomec, List.of(vaca))).andExpect(status().isCreated());
        String id = objectMapper.readTree(api.get(token, eventos() + "?animalId=" + vaca)
            .andReturn().getResponse().getContentAsString()).get("contenido").get(0).get("id").asText();
        api.get(token, url("animales") + "/" + vaca).andExpect(jsonPath("$.carencia").exists());

        api.delete(token, eventos() + "/" + id).andExpect(status().isNoContent());

        api.get(token, url("animales") + "/" + vaca).andExpect(jsonPath("$.carencia").doesNotExist());
    }

    // ---------- Permisos ----------

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 201", "SOCIO, 403"})
    void tratamientosLosCarganQuienesRegistranSanidad(Rol rol, int esperado) throws Exception {
        String vaca = crearAnimal("V-1");
        String ivomec = crearProducto("Ivomec", "ANTIPARASITARIO", 28);
        String tokenRol = tokenConRol(rol);

        api.post(tokenRol, eventos(), tratamiento(HOY, ivomec, List.of(vaca))).andExpect(status().is(esperado));
        api.get(tokenRol, eventos() + "?tipo=TRATAMIENTO").andExpect(status().isOk());
    }

    // ---------- Helpers ----------

    private String eventos() {
        return url("eventos");
    }

    private String productos() {
        return url("productos-sanitarios");
    }

    private String productos(String establecimiento) {
        return url(establecimiento, "productos-sanitarios");
    }

    private String url(String recurso) {
        return url(establecimientoId, recurso);
    }

    private static String url(String establecimiento, String recurso) {
        return "/api/establecimientos/" + establecimiento + "/" + recurso;
    }

    private static Map<String, Object> producto(String nombre, String tipo, int diasCarencia) {
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", nombre);
        body.put("tipo", tipo);
        body.put("diasCarencia", diasCarencia);
        return body;
    }

    private static Map<String, Object> datos(String productoId, String dosis, String via, String veterinario,
                                             Integer diasCarencia) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("productoId", productoId);
        datos.put("dosis", dosis);
        datos.put("via", via);
        datos.put("veterinario", veterinario);
        datos.put("diasCarencia", diasCarencia);
        return datos;
    }

    private static Map<String, Object> tratamiento(LocalDate fecha, String productoId, List<String> animalIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "TRATAMIENTO");
        body.put("fecha", fecha.toString());
        body.put("animalIds", animalIds);
        body.put("datos", datos(productoId, "5 ml", "SUBCUTANEA", null, null));
        return body;
    }

    private String crearProducto(String nombre, String tipo, int diasCarencia) throws Exception {
        return api.leer(api.post(token, productos(), producto(nombre, tipo, diasCarencia))
            .andExpect(status().isCreated()), "id");
    }

    private String crearAgrupacion(String recurso, String nombre) throws Exception {
        return api.leer(api.post(token, url(recurso), Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private String crearAnimal(String caravana) throws Exception {
        return crearAnimalEn(caravana, null, null);
    }

    private String crearAnimalEn(String caravana, String loteId, String rodeoId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", "HEMBRA");
        body.put("categoria", "VACA");
        body.put("loteId", loteId);
        body.put("rodeoId", rodeoId);
        return api.leer(api.post(token, url("animales"), body).andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    /** Usuario nuevo de la organización agregado al establecimiento con ese rol. */
    private String tokenConRol(Rol rol) throws Exception {
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, url("miembros"), Map.of("email", email, "rol", rol.name())).andExpect(status().isCreated());
        ResultActions login = api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD));
        return api.leer(login, "accessToken");
    }
}
