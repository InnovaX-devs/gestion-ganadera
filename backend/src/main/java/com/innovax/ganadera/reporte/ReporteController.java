package com.innovax.ganadera.reporte;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.reporte.dto.ExistenciasResponse;
import com.innovax.ganadera.reporte.dto.PesosResponse;

import lombok.RequiredArgsConstructor;

/**
 * Reportes (#32). El stock por categoría y lote sale de {@code GET /api/stock}; la exportación a
 * PDF y Excel se hace en la web.
 */
@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService service;

    /** {@code ?desde=2026-08-01&hasta=2026-10-31&loteId=...&categoria=NOVILLO}, todo opcional. */
    @GetMapping("/pesos")
    public PesosResponse pesos(
            @PathVariable UUID establecimientoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID loteId,
            @RequestParam(required = false) Categoria categoria) {
        return service.pesos(establecimientoId, desde, hasta, loteId, categoria);
    }

    @GetMapping("/existencias")
    public ExistenciasResponse existencias(@PathVariable UUID establecimientoId) {
        return service.existencias(establecimientoId);
    }
}
