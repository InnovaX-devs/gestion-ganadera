package com.innovax.ganadera.evento;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.innovax.ganadera.evento.dto.EventoRequest;
import com.innovax.ganadera.evento.dto.EventoResponse;
import com.innovax.ganadera.evento.dto.OperacionResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService service;

    /** Filtros: tipo, animalId, loteId, desde, hasta, pagina, tamanio. Más recientes primero. */
    @GetMapping
    public PaginaResponse<EventoResponse> listar(@PathVariable UUID establecimientoId,
                                                 @ModelAttribute EventoFiltro filtro) {
        return service.listar(establecimientoId, filtro);
    }

    @GetMapping("/{id}")
    public EventoResponse obtener(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.obtener(establecimientoId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OperacionResponse registrar(@PathVariable UUID establecimientoId,
                                       @Valid @RequestBody EventoRequest request) {
        return service.registrar(establecimientoId, request);
    }

    /** Anula el evento (no lo borra). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void anular(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.anular(establecimientoId, id);
    }
}
