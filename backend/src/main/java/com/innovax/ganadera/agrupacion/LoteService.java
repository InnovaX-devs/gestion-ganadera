package com.innovax.ganadera.agrupacion;

import org.springframework.stereotype.Service;

import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;

@Service
public class LoteService extends AgrupacionService<Lote> {

    public LoteService(LoteRepository repository, EstablecimientoRepository establecimientoRepository) {
        super(repository, establecimientoRepository);
    }

    @Override
    protected String nombreRecurso() {
        return "lote";
    }

    @Override
    protected Lote nueva(Establecimiento establecimiento, String nombre) {
        return new Lote(establecimiento, nombre);
    }
}
