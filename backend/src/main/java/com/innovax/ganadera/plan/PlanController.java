package com.innovax.ganadera.plan;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.plan.dto.PlanResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService service;

    /** Plan de la organización del usuario logueado, cuánto usa de cada límite y los planes disponibles. */
    @GetMapping
    public PlanResponse actual() {
        return service.actual();
    }
}
