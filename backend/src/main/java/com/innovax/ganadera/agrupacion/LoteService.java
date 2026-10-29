package com.innovax.ganadera.agrupacion;

import org.springframework.stereotype.Service;

import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;

@Service
public class LoteService extends AgrupacionService<Lote> {

    private final AnimalRepository animalRepository;

    public LoteService(LoteRepository repository, EstablecimientoRepository establecimientoRepository,
            AutorizacionService autorizacion, AnimalRepository animalRepository) {
        super(repository, establecimientoRepository, autorizacion);
        this.animalRepository = animalRepository;
    }

    @Override
    protected long contarAnimales(Lote lote) {
        return animalRepository.countByLoteIdAndEstado(lote.getId(), EstadoAnimal.ACTIVO);
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
