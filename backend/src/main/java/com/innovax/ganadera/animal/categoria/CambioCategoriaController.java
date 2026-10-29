package com.innovax.ganadera.animal.categoria;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.animal.categoria.dto.CambioCategoriaResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CambioCategoriaController {

    private final CategoriaService service;

    /** Historial de categorías del animal, el cambio más reciente primero. */
    @GetMapping("/api/establecimientos/{establecimientoId}/animales/{animalId}/cambios-categoria")
    public List<CambioCategoriaResponse> historial(@PathVariable UUID establecimientoId,
                                                   @PathVariable UUID animalId) {
        return service.historial(establecimientoId, animalId);
    }
}
