package com.innovax.ganadera.mercado;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.mercado.dto.HistoricoResponse;
import com.innovax.ganadera.mercado.dto.MercadoHoyResponse;

import lombok.RequiredArgsConstructor;

/**
 * Cotizaciones de mercado (#27). Son las mismas para todos: alcanza con estar logueado.
 */
@RestController
@RequestMapping("/api/mercado")
@RequiredArgsConstructor
public class MercadoController {

    private final CotizacionService service;

    @GetMapping("/hoy")
    public MercadoHoyResponse hoy() {
        return service.hoy();
    }

    /** {@code ?producto=TERNERO&producto=NOVILLO&desde=2026-10-01&hasta=2026-10-30}, todo opcional. */
    @GetMapping("/historico")
    public HistoricoResponse historico(
            @RequestParam(required = false) List<Producto> producto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.historico(producto, desde, hasta);
    }
}
