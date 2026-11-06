package com.innovax.ganadera.adjunto;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.innovax.ganadera.adjunto.dto.AdjuntoResponse;

import lombok.RequiredArgsConstructor;

/**
 * Subida como {@code multipart/form-data} con la parte {@code archivo} y, opcional, {@code cargaId}
 * (un UUID que genera el cliente: si reintenta con el mismo, no se duplica).
 */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}")
@RequiredArgsConstructor
public class AdjuntoController {

    private final AdjuntoService service;

    /**
     * Adjunta a una operación de eventos (el {@code operacionId} que devuelve {@code POST /eventos}).
     * Se ve en todos los eventos de la operación.
     */
    @PostMapping(value = "/eventos/operaciones/{operacionId}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AdjuntoResponse subirAEvento(@PathVariable UUID establecimientoId, @PathVariable UUID operacionId,
                                        @RequestPart("archivo") MultipartFile archivo,
                                        @RequestParam(required = false) UUID cargaId) {
        return service.subirAEvento(establecimientoId, operacionId, archivo, cargaId);
    }

    @PostMapping(value = "/animales/{animalId}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AdjuntoResponse subirAAnimal(@PathVariable UUID establecimientoId, @PathVariable UUID animalId,
                                        @RequestPart("archivo") MultipartFile archivo,
                                        @RequestParam(required = false) UUID cargaId) {
        return service.subirAAnimal(establecimientoId, animalId, archivo, cargaId);
    }

    /** Los archivos del animal, del más nuevo al más viejo. Los de sus eventos vienen en cada evento. */
    @GetMapping("/animales/{animalId}/adjuntos")
    public List<AdjuntoResponse> deAnimal(@PathVariable UUID establecimientoId, @PathVariable UUID animalId) {
        return service.deAnimal(establecimientoId, animalId);
    }

    /** El contenido. Las fotos se muestran en la página; los PDF se bajan. */
    @GetMapping("/adjuntos/{id}")
    public ResponseEntity<InputStreamResource> descargar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        var descarga = service.descargar(establecimientoId, id);
        var disposicion = (descarga.tipoContenido().startsWith("image/")
            ? ContentDisposition.inline()
            : ContentDisposition.attachment())
            .filename(descarga.nombre(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(descarga.tipoContenido()))
            .contentLength(descarga.tamanio())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
            // Un adjunto no cambia nunca: el navegador puede guardarlo (privado: es de esta organización)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
            .body(new InputStreamResource(descarga.contenido()));
    }

    @DeleteMapping("/adjuntos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID establecimientoId, @PathVariable UUID id) {
        service.eliminar(establecimientoId, id);
    }
}
