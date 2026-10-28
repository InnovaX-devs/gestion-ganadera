package com.innovax.ganadera.agrupacion;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/rodeos")
public class RodeoController extends AgrupacionController {

    public RodeoController(RodeoService service) {
        super(service);
    }
}
