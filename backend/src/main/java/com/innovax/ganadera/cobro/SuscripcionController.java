package com.innovax.ganadera.cobro;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.cobro.dto.SuscribirRequest;
import com.innovax.ganadera.cobro.dto.SuscribirResponse;
import com.innovax.ganadera.plan.PlanService;
import com.innovax.ganadera.plan.dto.PlanResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Suscripción de la organización del usuario logueado (#35). */
@RestController
@RequestMapping("/api/suscripcion")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService service;
    private final PlanService planService;

    /** Devuelve el link de Mercado Pago donde se paga. Solo el dueño de la cuenta. */
    @PostMapping
    public SuscribirResponse suscribir(@Valid @RequestBody SuscribirRequest request) {
        return service.suscribir(request.planCodigo());
    }

    /** Deja de cobrarse; el plan sigue hasta el último día pago. Solo el dueño de la cuenta. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar() {
        service.cancelar();
    }

    /** Consulta a Mercado Pago y devuelve el plan actualizado (al volver de pagar). */
    @PostMapping("/sincronizar")
    public PlanResponse sincronizar() {
        service.sincronizarOrganizacion();
        return planService.actual();
    }
}
