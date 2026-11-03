package com.innovax.ganadera.calendario;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.calendario.dto.PreferenciasDto;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** {@code /api/usuario/preferencias}: si el usuario logueado recibe las alertas por email (#34). */
@RestController
@RequestMapping("/api/usuario/preferencias")
@RequiredArgsConstructor
public class PreferenciasController {

    private final UsuarioRepository usuarioRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public PreferenciasDto leer() {
        return new PreferenciasDto(actual().isAlertasEmail());
    }

    @PutMapping
    @Transactional
    public PreferenciasDto guardar(@Valid @RequestBody PreferenciasDto request) {
        Usuario usuario = actual();
        usuario.setAlertasEmail(request.alertasEmail());
        return new PreferenciasDto(usuarioRepository.save(usuario).isAlertasEmail());
    }

    private Usuario actual() {
        var id = TenantContext.requerirUsuario().userId();
        return usuarioRepository.findById(id).orElseThrow(() -> NotFoundException.of("Usuario", id));
    }
}
