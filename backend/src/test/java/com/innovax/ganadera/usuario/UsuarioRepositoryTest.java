package com.innovax.ganadera.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;

@SpringBootTest
@ActiveProfiles("test")
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OrganizacionRepository organizacionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Organizacion organizacion;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM usuario");
        jdbc.update("DELETE FROM organizacion");
        organizacion = organizacionRepository.save(new Organizacion("Estancia La Prueba"));
    }

    @Test
    void seEncuentraPorEmailSinUsuarioLogueadoEIgnorandoMayusculas() {
        usuarioRepository.save(new Usuario(organizacion, "  Juan@Campo.com ", "hash", "Juan", "Pérez"));

        var encontrado = usuarioRepository.findByEmailIgnoreCase("JUAN@campo.com");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getEmail()).isEqualTo("juan@campo.com");
        assertThat(encontrado.get().getCreatedAt()).isNotNull();
        assertThat(usuarioRepository.existsByEmailIgnoreCase("juan@campo.com")).isTrue();
    }

    @Test
    void elEmailEsUnico() {
        usuarioRepository.save(new Usuario(organizacion, "juan@campo.com", "hash", "Juan", null));

        assertThatThrownBy(() ->
            usuarioRepository.saveAndFlush(new Usuario(organizacion, "JUAN@campo.com", "hash", "Otro", null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
