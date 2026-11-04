package com.innovax.ganadera.stock;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.stock.dto.StockResponse;

import lombok.RequiredArgsConstructor;

/** {@code GET /api/stock?establecimientoId=...}: stock calculado del establecimiento (#25). */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService service;

    @GetMapping
    public StockResponse stock(@RequestParam UUID establecimientoId) {
        return service.calcular(establecimientoId);
    }
}
