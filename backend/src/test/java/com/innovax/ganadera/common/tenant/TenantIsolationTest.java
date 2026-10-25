package com.innovax.ganadera.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Criterio de aceptación del issue #8: un usuario no puede leer datos de otra organización.
 *
 * <p>La clase NO es @Transactional a propósito: cada llamada al repositorio abre su propia sesión,
 * igual que en un request real, así no se cuela nada por la caché de primer nivel de Hibernate.
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationTest {

    private static final UUID ORG_A = UUID.randomUUID();
    private static final UUID ORG_B = UUID.randomUUID();

    @Autowired
    private RegistroPruebaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM registro_prueba");
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void alGuardarSeAsignaLaOrganizacionDelUsuarioLogueado() {
        loguearComo(ORG_A);
        RegistroPrueba guardado = repository.save(new RegistroPrueba("vaca 1"));

        UUID orgEnBase = jdbc.queryForObject(
            "SELECT organizacion_id FROM registro_prueba WHERE id = ?", UUID.class, guardado.getId());
        assertThat(orgEnBase).isEqualTo(ORG_A);
    }

    @Test
    void cadaOrganizacionSoloVeSusDatos() {
        loguearComo(ORG_A);
        repository.save(new RegistroPrueba("de A 1"));
        repository.save(new RegistroPrueba("de A 2"));

        loguearComo(ORG_B);
        repository.save(new RegistroPrueba("de B 1"));

        loguearComo(ORG_A);
        assertThat(repository.findAll()).extracting(RegistroPrueba::getNombre)
            .containsExactlyInAnyOrder("de A 1", "de A 2");
        assertThat(repository.count()).isEqualTo(2);

        loguearComo(ORG_B);
        assertThat(repository.findAll()).extracting(RegistroPrueba::getNombre)
            .containsExactly("de B 1");
    }

    @Test
    void noSePuedeLeerPorIdUnRegistroDeOtraOrganizacion() {
        loguearComo(ORG_B);
        UUID idDeB = repository.save(new RegistroPrueba("secreto de B")).getId();

        loguearComo(ORG_A);
        assertThat(repository.findById(idDeB)).isEmpty();
        assertThat(repository.existsById(idDeB)).isFalse();
    }

    @Test
    void lasConsultasDerivadasTambienFiltranPorOrganizacion() {
        loguearComo(ORG_A);
        repository.save(new RegistroPrueba("mismo nombre"));
        loguearComo(ORG_B);
        repository.save(new RegistroPrueba("mismo nombre"));

        loguearComo(ORG_A);
        List<RegistroPrueba> resultado = repository.findByNombre("mismo nombre");
        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().getOrganizacionId()).isEqualTo(ORG_A);
    }

    @Test
    void sinUsuarioLogueadoNoSeVeNada() {
        loguearComo(ORG_A);
        repository.save(new RegistroPrueba("de A"));

        SecurityContextHolder.clearContext();
        assertThat(repository.findAll()).isEmpty();
    }

    private void loguearComo(UUID organizacionId) {
        var usuario = new AuthenticatedUser(UUID.randomUUID(), organizacionId, "test@local.dev");
        var auth = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
