package com.innovax.ganadera.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.admin.dto.PrecioRequest;
import com.innovax.ganadera.plan.PlanService;
import com.innovax.ganadera.plan.dto.PlanResponse.PlanInfo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Precios de los planes, desde el panel de Innovax (#35). Las suscripciones ya hechas siguen con
 * el precio que tenían: el nuevo rige para las que se hagan después.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/planes")
@RequiredArgsConstructor
public class AdminPlanesController {

    private final PlanService planService;
    private final Administradores administradores;

    @GetMapping
    public List<PlanInfo> listar() {
        administradores.exigir();
        return planService.planes();
    }

    @PutMapping("/{codigo}")
    public PlanInfo cambiarPrecio(@PathVariable String codigo, @Valid @RequestBody PrecioRequest request) {
        String admin = administradores.exigir();
        PlanInfo plan = planService.cambiarPrecio(codigo, request.precioMensual());
        log.info("Administración: {} puso el precio del plan {} en {}", admin, plan.codigo(), plan.precioMensual());
        return plan;
    }
}
