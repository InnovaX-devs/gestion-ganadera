package com.innovax.ganadera.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #25: stock calculado por categoría, sexo, lote y rodeo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockControllerTest {

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

    // ---------- Totales ----------

    @Test
    void sinAnimalesTodoEnCeroYMuestraLosLotesYRodeosVacios() throws Exception {
        crearAgrupacion("lotes", "Recría");
        crearAgrupacion("rodeos", "Cría");

        stock()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.establecimientoId").value(establecimientoId))
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.porSexo.HEMBRA").value(0))
            .andExpect(jsonPath("$.porSexo.MACHO").value(0))
            .andExpect(jsonPath("$.porCategoria.length()").value(Categoria.values().length))
            .andExpect(jsonPath("$.porCategoria.TORO").value(0))
            .andExpect(jsonPath("$.porLote.length()").value(1))
            .andExpect(jsonPath("$.porLote[0].nombre").value("Recría"))
            .andExpect(jsonPath("$.porLote[0].total").value(0))
            .andExpect(jsonPath("$.porRodeo.length()").value(1))
            .andExpect(jsonPath("$.porRodeo[0].total").value(0));
    }

    @Test
    void cuentaPorSexoCategoriaLoteYRodeo() throws Exception {
        String recria = crearAgrupacion("lotes", "Recría");
        String vacas = crearAgrupacion("lotes", "Vacas potrero 3");
        String cria = crearAgrupacion("rodeos", "Cría");

        crear("V-1", Categoria.VACA, vacas, cria);
        crear("V-2", Categoria.VACA, vacas, cria);
        crear("V-3", Categoria.VACA, null, cria);
        crear("H-1", Categoria.TERNERA, recria, null);
        crear("M-1", Categoria.TERNERO, recria, null);
        crear("T-1", Categoria.TORO, null, null);

        stock()
            .andExpect(jsonPath("$.total").value(6))
            .andExpect(jsonPath("$.porSexo.HEMBRA").value(4))
            .andExpect(jsonPath("$.porSexo.MACHO").value(2))
            .andExpect(jsonPath("$.porCategoria.VACA").value(3))
            .andExpect(jsonPath("$.porCategoria.TERNERA").value(1))
            .andExpect(jsonPath("$.porCategoria.NOVILLO").value(0))
            // Lotes ordenados por nombre y al final los que no tienen lote
            .andExpect(jsonPath("$.porLote.length()").value(3))
            .andExpect(jsonPath("$.porLote[0].id").value(recria))
            .andExpect(jsonPath("$.porLote[0].total").value(2))
            .andExpect(jsonPath("$.porLote[0].porSexo.MACHO").value(1))
            .andExpect(jsonPath("$.porLote[0].porCategoria.TERNERO").value(1))
            .andExpect(jsonPath("$.porLote[1].nombre").value("Vacas potrero 3"))
            .andExpect(jsonPath("$.porLote[1].total").value(2))
            .andExpect(jsonPath("$.porLote[2].id").doesNotExist())
            .andExpect(jsonPath("$.porLote[2].nombre").doesNotExist())
            .andExpect(jsonPath("$.porLote[2].total").value(2))
            .andExpect(jsonPath("$.porRodeo.length()").value(2))
            .andExpect(jsonPath("$.porRodeo[0].total").value(3))
            .andExpect(jsonPath("$.porRodeo[0].porCategoria.VACA").value(3))
            .andExpect(jsonPath("$.porRodeo[1].id").doesNotExist())
            .andExpect(jsonPath("$.porRodeo[1].total").value(3));
    }

    @Test
    void siTodosTienenLoteNoApareceElGrupoSinLote() throws Exception {
        String lote = crearAgrupacion("lotes", "Recría");
        crear("A-1", Categoria.VAQUILLONA, lote, null);

        stock()
            .andExpect(jsonPath("$.porLote.length()").value(1))
            .andExpect(jsonPath("$.porLote[0].id").value(lote))
            .andExpect(jsonPath("$.porRodeo.length()").value(1))
            .andExpect(jsonPath("$.porRodeo[0].id").doesNotExist());
    }

    @Test
    void soloCuentanLosActivosDeEseEstablecimiento() throws Exception {
        String vendida = crear("V-1", Categoria.VACA, null, null);
        String muerto = crear("T-1", Categoria.TORO, null, null);
        crear("V-2", Categoria.VACA, null, null);
        cambiarEstado(vendida, "VENDIDO");
        cambiarEstado(muerto, "MUERTO");

        String otro = crearEstablecimiento(token, "El Ombú");
        api.post(token, animalesUrl(otro), animal("Z-1", Categoria.NOVILLO, null, null))
            .andExpect(status().isCreated());

        stock()
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.porCategoria.VACA").value(1))
            .andExpect(jsonPath("$.porCategoria.TORO").value(0))
            .andExpect(jsonPath("$.porCategoria.NOVILLO").value(0));
    }

    @Test
    void elStockSeRecalculaAlMoverAnimalesDeLote() throws Exception {
        String origen = crearAgrupacion("lotes", "Origen");
        String destino = crearAgrupacion("lotes", "Destino");
        String a1 = crear("A-1", Categoria.VACA, origen, null);
        String a2 = crear("A-2", Categoria.VACA, origen, null);

        api.post(token, animalesUrl(establecimientoId) + "/asignar",
                Map.of("animalIds", List.of(a1, a2), "campo", "LOTE", "destinoId", destino))
            .andExpect(status().isOk());

        stock()
            .andExpect(jsonPath("$.porLote[0].nombre").value("Destino"))
            .andExpect(jsonPath("$.porLote[0].total").value(2))
            .andExpect(jsonPath("$.porLote[1].nombre").value("Origen"))
            .andExpect(jsonPath("$.porLote[1].total").value(0));
    }

    // ---------- Consistencia contra la lista de animales ----------

    /**
     * Arma un establecimiento con animales repartidos al azar (semilla fija, así el test es
     * reproducible) entre categorías, lotes y rodeos, con algunos vendidos y muertos, y ruido en otro
     * establecimiento y en otra organización. El stock tiene que coincidir con la lista de animales,
     * contada de dos formas: recorriendo la lista completa y pidiéndole al listado cada filtro.
     */
    @Test
    void elStockCoincideConLaListaDeAnimales() throws Exception {
        List<String> lotes = new ArrayList<>(List.of(
            crearAgrupacion("lotes", "Recría"),
            crearAgrupacion("lotes", "Vacas potrero 3"),
            crearAgrupacion("lotes", "Engorde"),
            crearAgrupacion("lotes", "Vacío")));
        List<String> rodeos = new ArrayList<>(List.of(
            crearAgrupacion("rodeos", "Cría"),
            crearAgrupacion("rodeos", "Invernada")));
        // null = sin asignar. "Vacío" no se usa nunca, para probar los lotes sin animales.
        List<String> lotesPosibles = new ArrayList<>(lotes.subList(0, 3));
        lotesPosibles.add(null);
        List<String> rodeosPosibles = new ArrayList<>(rodeos);
        rodeosPosibles.add(null);

        Random random = new Random(25);
        Categoria[] categorias = Categoria.values();
        List<String> creados = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            Categoria categoria = categorias[random.nextInt(categorias.length)];
            String lote = lotesPosibles.get(random.nextInt(lotesPosibles.size()));
            String rodeo = rodeosPosibles.get(random.nextInt(rodeosPosibles.size()));
            creados.add(crear("C-" + i, categoria, lote, rodeo));
        }
        for (int i = 0; i < 8; i++) {
            cambiarEstado(creados.get(i * 7), i % 2 == 0 ? "VENDIDO" : "MUERTO");
        }

        String otroEstablecimiento = crearEstablecimiento(token, "El Ombú");
        api.post(token, animalesUrl(otroEstablecimiento), animal("Z-1", Categoria.VACA, null, null))
            .andExpect(status().isCreated());
        String tokenB = api.registrarCuenta("Organización B");
        String establecimientoB = crearEstablecimiento(tokenB, "Campo B");
        api.post(tokenB, animalesUrl(establecimientoB), animal("Z-1", Categoria.TORO, null, null))
            .andExpect(status().isCreated());

        JsonNode stock = json(stock().andExpect(status().isOk()));
        JsonNode lista = json(api.get(token, animalesUrl(establecimientoId) + "?tamanio=200"));
        List<JsonNode> animales = new ArrayList<>();
        lista.get("contenido").forEach(animales::add);
        assertThat(lista.get("total").asLong()).isEqualTo(animales.size()).isEqualTo(52);

        // 1. Recorriendo la lista completa
        assertThat(stock.get("total").asLong()).isEqualTo(animales.size());
        assertThat(conteos(stock.get("porSexo"))).isEqualTo(contar(animales, "sexo"));
        assertThat(conteos(stock.get("porCategoria"))).isEqualTo(contar(animales, "categoria"));
        verificarGrupos(stock.get("porLote"), animales, "lote", lotes);
        verificarGrupos(stock.get("porRodeo"), animales, "rodeo", rodeos);

        // 2. Contra los filtros del listado
        for (Sexo sexo : Sexo.values()) {
            assertThat(stock.at("/porSexo/" + sexo).asLong())
                .as("sexo %s", sexo).isEqualTo(totalListado("sexo=" + sexo));
        }
        for (Categoria categoria : categorias) {
            assertThat(stock.at("/porCategoria/" + categoria).asLong())
                .as("categoría %s", categoria).isEqualTo(totalListado("categoria=" + categoria));
        }
        verificarGruposContraFiltros(stock.get("porLote"), "loteId", "sinLote");
        verificarGruposContraFiltros(stock.get("porRodeo"), "rodeoId", "sinRodeo");
    }

    // ---------- Permisos y errores ----------

    @ParameterizedTest
    @EnumSource(Rol.class)
    void todosLosRolesVenElStock(Rol rol) throws Exception {
        crear("V-1", Categoria.VACA, null, null);

        api.get(tokenConRol(rol), stockUrl(establecimientoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void otraOrganizacionRecibe404() throws Exception {
        crear("V-1", Categoria.VACA, null, null);
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, stockUrl(establecimientoId)).andExpect(status().isNotFound());
    }

    @Test
    void establecimientoInexistenteODadoDeBajaDevuelve404() throws Exception {
        api.get(token, stockUrl(UUID.randomUUID().toString())).andExpect(status().isNotFound());

        String otro = crearEstablecimiento(token, "El Ombú");
        api.delete(token, "/api/establecimientos/" + otro).andExpect(status().isNoContent());
        api.get(token, stockUrl(otro)).andExpect(status().isNotFound());
    }

    @Test
    void sinEstablecimientoOConIdInvalidoDevuelve400() throws Exception {
        api.get(token, "/api/stock")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Falta el parámetro establecimientoId"));
        api.get(token, "/api/stock?establecimientoId=no-es-un-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        api.get(null, stockUrl(establecimientoId)).andExpect(status().isUnauthorized());
    }

    // ---------- Verificaciones de consistencia ----------

    /** Cada grupo (lote o rodeo) del stock tiene que coincidir con los animales de la lista que tienen ese grupo. */
    private void verificarGrupos(JsonNode grupos, List<JsonNode> animales, String campo, List<String> activos) {
        Map<String, List<JsonNode>> porGrupo = new HashMap<>();
        for (JsonNode animal : animales) {
            JsonNode grupo = animal.get(campo);
            String id = grupo == null || grupo.isNull() ? null : grupo.get("id").asText();
            porGrupo.computeIfAbsent(id, k -> new ArrayList<>()).add(animal);
        }

        long suma = 0;
        List<String> ids = new ArrayList<>();
        for (JsonNode grupo : grupos) {
            String id = grupo.hasNonNull("id") ? grupo.get("id").asText() : null;
            ids.add(id);
            List<JsonNode> esperados = porGrupo.getOrDefault(id, List.of());
            assertThat(grupo.get("total").asLong()).as("%s %s", campo, id).isEqualTo(esperados.size());
            assertThat(conteos(grupo.get("porSexo"))).as("%s %s por sexo", campo, id)
                .isEqualTo(contar(esperados, "sexo"));
            assertThat(conteos(grupo.get("porCategoria"))).as("%s %s por categoría", campo, id)
                .isEqualTo(contar(esperados, "categoria"));
            suma += grupo.get("total").asLong();
        }
        assertThat(suma).as("suma de %s", campo).isEqualTo(animales.size());
        // Están todos los activos (también los vacíos) y, si corresponde, el grupo sin asignar
        assertThat(ids).containsAll(activos);
        assertThat(ids.contains(null)).isEqualTo(porGrupo.containsKey(null));
    }

    private void verificarGruposContraFiltros(JsonNode grupos, String filtroId, String filtroSin) throws Exception {
        for (JsonNode grupo : grupos) {
            String filtro = grupo.hasNonNull("id") ? filtroId + "=" + grupo.get("id").asText() : filtroSin + "=true";
            assertThat(grupo.get("total").asLong()).as(filtro).isEqualTo(totalListado(filtro));
        }
    }

    /** Total que devuelve el listado de animales con ese filtro. */
    private long totalListado(String filtro) throws Exception {
        return json(api.get(token, animalesUrl(establecimientoId) + "?" + filtro).andExpect(status().isOk()))
            .get("total").asLong();
    }

    /** Pasa un mapa del stock a {clave: cantidad}, dejando afuera los ceros. */
    private static Map<String, Long> conteos(JsonNode mapa) {
        Map<String, Long> resultado = new HashMap<>();
        mapa.properties().forEach(e -> {
            if (e.getValue().asLong() > 0) resultado.put(e.getKey(), e.getValue().asLong());
        });
        return resultado;
    }

    private static Map<String, Long> contar(List<JsonNode> animales, String campo) {
        Map<String, Long> resultado = new HashMap<>();
        animales.forEach(a -> resultado.merge(a.get(campo).asText(), 1L, Long::sum));
        return resultado;
    }

    // ---------- Helpers ----------

    private ResultActions stock() throws Exception {
        return api.get(token, stockUrl(establecimientoId));
    }

    private static String stockUrl(String establecimiento) {
        return "/api/stock?establecimientoId=" + establecimiento;
    }

    private static String animalesUrl(String establecimiento) {
        return "/api/establecimientos/" + establecimiento + "/animales";
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> animal(String caravana, Categoria categoria, String loteId, String rodeoId) {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", categoria.sexo().name());
        body.put("categoria", categoria.name());
        body.put("loteId", loteId);
        body.put("rodeoId", rodeoId);
        return body;
    }

    private String crear(String caravana, Categoria categoria, String loteId, String rodeoId) throws Exception {
        return api.leer(api.post(token, animalesUrl(establecimientoId), animal(caravana, categoria, loteId, rodeoId))
            .andExpect(status().isCreated()), "id");
    }

    /** Las ventas y bajas todavía no tienen endpoint (#23): se simulan cambiando el estado en la base. */
    private void cambiarEstado(String animalId, String estado) {
        jdbc.update("UPDATE animal SET estado = ? WHERE id = ?", estado, UUID.fromString(animalId));
    }

    private String crearAgrupacion(String tipo, String nombre) throws Exception {
        return api.leer(api.post(token, "/api/establecimientos/" + establecimientoId + "/" + tipo,
                Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    /** Usuario nuevo de la misma organización, agregado al establecimiento con ese rol (como en AnimalControllerTest). */
    private String tokenConRol(Rol rol) throws Exception {
        if (rol == Rol.DUENO) return token;
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, "/api/establecimientos/" + establecimientoId + "/miembros",
                Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated());
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
