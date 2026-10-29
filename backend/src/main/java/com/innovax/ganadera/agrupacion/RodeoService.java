package com.innovax.ganadera.agrupacion;

import org.springframework.stereotype.Service;

import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;

@Service
public class RodeoService extends AgrupacionService<Rodeo> {

    private final AnimalRepository animalRepository;

    public RodeoService(RodeoRepository repository, EstablecimientoRepository establecimientoRepository,
            AutorizacionService autorizacion, AnimalRepository animalRepository) {
        super(repository, establecimientoRepository, autorizacion);
        this.animalRepository = animalRepository;
    }

    @Override
    protected long contarAnimales(Rodeo rodeo) {
        return animalRepository.countByRodeoIdAndEstado(rodeo.getId(), EstadoAnimal.ACTIVO);
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
