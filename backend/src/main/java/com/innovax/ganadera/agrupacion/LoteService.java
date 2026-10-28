package com.innovax.ganadera.agrupacion;

import org.springframework.stereotype.Service;

import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;

@Service
public class LoteService extends AgrupacionService<Lote> {

    public LoteService(LoteRepository repository, EstablecimientoRepository establecimientoRepository,
            AutorizacionService autorizacion) {
        super(repository, establecimientoRepository, autorizacion);
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
