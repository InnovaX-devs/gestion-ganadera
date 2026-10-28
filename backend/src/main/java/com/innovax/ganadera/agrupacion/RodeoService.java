package com.innovax.ganadera.agrupacion;

import org.springframework.stereotype.Service;

import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;

@Service
public class RodeoService extends AgrupacionService<Rodeo> {

    public RodeoService(RodeoRepository repository, EstablecimientoRepository establecimientoRepository) {
        super(repository, establecimientoRepository);
    }

    @Override
    protected String nombreRecurso() {
        return "rodeo";
    }

    @Override
    protected Rodeo nueva(Establecimiento establecimiento, String nombre) {
        return new Rodeo(establecimiento, nombre);
    }
}
