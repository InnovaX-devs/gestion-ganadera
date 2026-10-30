package com.innovax.ganadera.clima;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.clima.dto.ClimaResponse;
import com.innovax.ganadera.common.error.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** Clima del establecimiento (#29). Lo ve cualquier miembro. */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/clima")
@RequiredArgsConstructor
public class ClimaController {

    private final ClimaService service;

    @GetMapping
    public ClimaResponse clima(@PathVariable UUID establecimientoId) {
        return service.clima(establecimientoId);
    }

    /** Propio de este controller: tiene prioridad sobre el manejador general (que daría 500). */
    @ExceptionHandler(ClimaNoDisponibleException.class)
    ResponseEntity<ApiError> noDisponible(ClimaNoDisponibleException ex, HttpServletRequest req) {
        HttpStatus estado = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(estado).body(new ApiError(Instant.now(), estado.value(),
            estado.getReasonPhrase(), ex.getMessage(), req.getRequestURI(), null));
    }
}
