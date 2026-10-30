package com.innovax.ganadera.animal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.animal.dto.AnimalFiltro;
import com.innovax.ganadera.animal.importacion.ImportacionAnimalesService;
import com.innovax.ganadera.animal.importacion.dto.ImportacionRequest;
import com.innovax.ganadera.animal.importacion.dto.ImportacionResponse;
import com.innovax.ganadera.animal.dto.AnimalRequest;
import com.innovax.ganadera.animal.dto.AnimalResponse;
import com.innovax.ganadera.animal.dto.AsignacionRequest;
import com.innovax.ganadera.common.dto.PaginaResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/animales")
@RequiredArgsConstructor
public class AnimalController {

    private final AnimalService service;
    private final ImportacionAnimalesService importacion;

    /** Filtros por query param: q, categoria, sexo, loteId, rodeoId, sinLote, sinRodeo, estado, pagina, tamanio. */
    @GetMapping
    public PaginaResponse<AnimalResponse> listar(@PathVariable UUID establecimientoId,
                                                 @ModelAttribute AnimalFiltro filtro) {
        return service.listar(establecimientoId, filtro);
    }

    @GetMapping("/{id}")
    public AnimalResponse obtener(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.obtener(establecimientoId, id);
    }

    /** Hijos del animal, sea madre o padre. Incluye vendidos y muertos (para la genealogía). */
    @GetMapping("/{id}/crias")
    public List<AnimalResponse> crias(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        return service.crias(establecimientoId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnimalResponse crear(@PathVariable UUID establecimientoId, @Valid @RequestBody AnimalRequest request) {
        return service.crear(establecimientoId, request);
    }

    /** Asignación masiva de lote o rodeo. Devuelve cuántos animales se actualizaron. */
    @PostMapping("/asignar")
    public Map<String, Integer> asignar(@PathVariable UUID establecimientoId,
                                        @Valid @RequestBody AsignacionRequest request) {
        return Map.of("actualizados", service.asignar(establecimientoId, request));
    }

    /**
     * Importación desde planilla (#31). Con {@code confirmar=false} (vista previa) solo revisa y
     * devuelve los errores por fila; con {@code confirmar=true} guarda todo o nada.
     */
    @PostMapping("/importar")
    public ImportacionResponse importar(@PathVariable UUID establecimientoId,
                                        @RequestParam(defaultValue = "false") boolean confirmar,
                                        @Valid @RequestBody ImportacionRequest request) {
        return importacion.importar(establecimientoId, request, confirmar);
    }

    @PutMapping("/{id}")
    public AnimalResponse actualizar(@PathVariable UUID establecimientoId, @PathVariable UUID id,
                                     @Valid @RequestBody AnimalRequest request) {
        return service.actualizar(establecimientoId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.eliminar(establecimientoId, id);
    }
}
