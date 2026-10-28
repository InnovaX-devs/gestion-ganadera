package com.innovax.ganadera.agrupacion;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/establecimientos/{establecimientoId}/lotes")
public class LoteController extends AgrupacionController {

    public LoteController(LoteService service) {
        super(service);
    }
}
