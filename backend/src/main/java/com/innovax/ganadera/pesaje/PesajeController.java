package com.innovax.ganadera.pesaje;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.common.dto.PaginaResponse;
import com.innovax.ganadera.evento.dto.EventoFiltro;
import com.innovax.ganadera.evento.dto.OperacionResponse;
import com.innovax.ganadera.pesaje.dto.PesajeGrillaRequest;
import com.innovax.ganadera.pesaje.dto.PesajeResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Pesajes (issue #21), anidados en el establecimiento. */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/pesajes")
@RequiredArgsConstructor
public class PesajeController {

    private final PesajeService service;

    /** Filtros: animalId, desde, hasta, pagina, tamanio. Más recientes primero, con la ganancia diaria. */
    @GetMapping
    public PaginaResponse<PesajeResponse> listar(@PathVariable UUID establecimientoId,
                                                 @ModelAttribute EventoFiltro filtro) {
        return service.listar(establecimientoId, filtro);
    }

    /** Carga rápida: varios animales por caravana, cada uno con su peso. Todo o nada. */
    @PostMapping("/grilla")
    @ResponseStatus(HttpStatus.CREATED)
    public OperacionResponse registrarGrilla(@PathVariable UUID establecimientoId,
                                             @Valid @RequestBody PesajeGrillaRequest request) {
        return service.registrarGrilla(establecimientoId, request);
    }
}
