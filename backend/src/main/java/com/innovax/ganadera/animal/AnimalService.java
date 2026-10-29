package com.innovax.ganadera.animal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.Rodeo;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.animal.dto.AnimalFiltro;
import com.innovax.ganadera.animal.dto.AnimalRequest;
import com.innovax.ganadera.animal.dto.AnimalResponse;
import com.innovax.ganadera.animal.dto.AsignacionRequest;
import com.innovax.ganadera.common.dto.PaginaResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;

import lombok.RequiredArgsConstructor;

/**
 * Alta, edición y consulta de animales de un establecimiento. Ver: cualquier miembro. Cargar y
 * editar: quien tenga {@link Permiso#GESTIONAR_ANIMALES}.
 *
 * <p>Las bajas (venta, muerte) no se hacen acá: son eventos (#23) que cambian el estado. El
 * {@link #eliminar} es solo para borrar un animal cargado por error.
 */
@Service
@RequiredArgsConstructor
public class AnimalService {

    private final AnimalRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;
    private final AutorizacionService autorizacion;
    private final EventoRepository eventoRepository;

    /** Listado paginado con filtros (#14). Ordenado por caravana. */
    @Transactional(readOnly = true)
    public PaginaResponse<AnimalResponse> listar(UUID establecimientoId, AnimalFiltro filtro) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoActivo(establecimientoId);
        var pageable = PageRequest.of(filtro.paginaValida(), filtro.tamanioValido(), Sort.by("caravana"));
        return PaginaResponse.de(repository.findAll(AnimalSpecs.de(establecimientoId, filtro), pageable),
            AnimalResponse::from);
    }

    /**
     * Asigna (o quita, si destinoId es null) el mismo lote o rodeo a varios animales. Todos tienen
     * que ser activos y del establecimiento; si alguno no lo es, no se cambia ninguno.
     *
     * <p>Cuando exista el evento Movimiento (#22), esto va a dejar registro en el historial.
     */
    @Transactional
    public int asignar(UUID establecimientoId, AsignacionRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_ANIMALES);
        establecimientoActivo(establecimientoId);
        Set<UUID> ids = new HashSet<>(request.animalIds());
        List<Animal> animales = repository.findAllById(ids).stream()
            .filter(a -> a.getEstablecimiento().getId().equals(establecimientoId) && a.isActivo())
            .toList();
        if (animales.size() != ids.size()) {
            throw new BusinessException("Alguno de los animales elegidos no está en este establecimiento");
        }
        switch (request.campo()) {
            case LOTE -> {
                Lote lote = lote(request.destinoId(), establecimientoId);
                animales.forEach(a -> a.setLote(lote));
            }
            case RODEO -> {
                Rodeo rodeo = rodeo(request.destinoId(), establecimientoId);
                animales.forEach(a -> a.setRodeo(rodeo));
            }
        }
        return animales.size();
    }

    @Transactional(readOnly = true)
    public AnimalResponse obtener(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        return AnimalResponse.from(buscar(establecimientoId, id));
    }

    @Transactional(readOnly = true)
    public List<AnimalResponse> crias(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        buscar(establecimientoId, id);
        return repository.buscarCrias(id).stream().map(AnimalResponse::from).toList();
    }

    @Transactional
    public AnimalResponse crear(UUID establecimientoId, AnimalRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_ANIMALES);
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);
        Animal animal = new Animal(establecimiento, request.caravana(), request.sexo(), request.categoria());
        aplicar(request, animal);
        return AnimalResponse.from(repository.save(animal));
    }

    @Transactional
    public AnimalResponse actualizar(UUID establecimientoId, UUID id, AnimalRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_ANIMALES);
        Animal animal = buscar(establecimientoId, id);
        aplicar(request, animal);
        return AnimalResponse.from(repository.saveAndFlush(animal));
    }

    /** Borrado definitivo, solo para corregir una carga equivocada. No se puede si tiene crías o eventos. */
    @Transactional
    public void eliminar(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_ANIMALES);
        Animal animal = buscar(establecimientoId, id);
        if (repository.existsByMadreIdOrPadreId(id, id)) {
            throw new BusinessException("No se puede eliminar: figura como madre o padre de otros animales");
        }
        if (eventoRepository.existsByAnimalIdAndDeletedAtIsNull(id)) {
            throw new BusinessException("No se puede eliminar: tiene eventos registrados. Anulalos primero.");
        }
        eventoRepository.borrarAnuladosDelAnimal(id);
        repository.delete(animal);
    }

    // ---------- Reglas ----------

    /** Copia los datos del pedido al animal, validando todo lo que cruza información. */
    private void aplicar(AnimalRequest request, Animal animal) {
        UUID establecimientoId = animal.getEstablecimiento().getId();

        if (request.categoria().sexo() != request.sexo()) {
            throw new BusinessException("La categoría " + texto(request.categoria())
                + " no corresponde a un animal " + texto(request.sexo()));
        }

        animal.setCaravana(request.caravana());
        animal.setCaravanaElectronica(request.caravanaElectronica());
        validarCaravanas(animal);

        animal.setSexo(request.sexo());
        animal.setCategoria(request.categoria());
        animal.setCastrado(request.categoria().castrado(request.castrado()));
        animal.setRaza(request.raza());
        animal.setFechaNacimiento(request.fechaNacimiento());
        animal.setFechaNacimientoEstimada(request.fechaNacimiento() != null && request.fechaNacimientoEstimada());
        animal.setObservaciones(request.observaciones());
        animal.setLote(lote(request.loteId(), establecimientoId));
        animal.setRodeo(rodeo(request.rodeoId(), establecimientoId));
        animal.setMadre(progenitor(request.madreId(), animal, Sexo.HEMBRA,
            "Un animal no puede ser su propia madre", "La madre elegida no existe", "La madre tiene que ser hembra"));
        animal.setPadre(progenitor(request.padreId(), animal, Sexo.MACHO,
            "Un animal no puede ser su propio padre", "El padre elegido no existe", "El padre tiene que ser macho"));
    }

    private void validarCaravanas(Animal animal) {
        UUID establecimientoId = animal.getEstablecimiento().getId();
        boolean caravanaRepetida = animal.isNew()
            ? repository.existsByEstablecimientoIdAndCaravanaAndEstado(
                establecimientoId, animal.getCaravana(), EstadoAnimal.ACTIVO)
            : repository.existsByEstablecimientoIdAndCaravanaAndEstadoAndIdNot(
                establecimientoId, animal.getCaravana(), EstadoAnimal.ACTIVO, animal.getId());
        if (caravanaRepetida) {
            throw new ConflictException("Ya hay un animal con la caravana " + animal.getCaravana()
                + " en este establecimiento");
        }

        String electronica = animal.getCaravanaElectronica();
        if (electronica == null) return;
        boolean electronicaRepetida = animal.isNew()
            ? repository.existsByCaravanaElectronicaAndEstado(electronica, EstadoAnimal.ACTIVO)
            : repository.existsByCaravanaElectronicaAndEstadoAndIdNot(electronica, EstadoAnimal.ACTIVO, animal.getId());
        if (electronicaRepetida) {
            throw new ConflictException("Ya hay un animal con la caravana electrónica " + electronica);
        }
    }

    private Lote lote(UUID loteId, UUID establecimientoId) {
        if (loteId == null) return null;
        return loteRepository.findByIdAndEstablecimientoIdAndActivoTrue(loteId, establecimientoId)
            .orElseThrow(() -> new BusinessException("El lote elegido no existe en este establecimiento"));
    }

    private Rodeo rodeo(UUID rodeoId, UUID establecimientoId) {
        if (rodeoId == null) return null;
        return rodeoRepository.findByIdAndEstablecimientoIdAndActivoTrue(rodeoId, establecimientoId)
            .orElseThrow(() -> new BusinessException("El rodeo elegido no existe en este establecimiento"));
    }

    /**
     * La madre o el padre pueden estar en cualquier establecimiento de la organización (o ya
     * vendidos), pero tienen que ser del sexo correcto y no pueden ser el mismo animal.
     */
    private Animal progenitor(UUID id, Animal hijo, Sexo sexoEsperado,
                              String esElMismo, String noExiste, String sexoIncorrecto) {
        if (id == null) return null;
        if (id.equals(hijo.getId())) {
            throw new BusinessException(esElMismo);
        }
        Animal progenitor = repository.findById(id).orElseThrow(() -> new BusinessException(noExiste));
        if (progenitor.getSexo() != sexoEsperado) {
            throw new BusinessException(sexoIncorrecto);
        }
        return progenitor;
    }

    private Establecimiento establecimientoActivo(UUID id) {
        return establecimientoRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    private Animal buscar(UUID establecimientoId, UUID id) {
        establecimientoActivo(establecimientoId);
        return repository.findByIdAndEstablecimientoId(id, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Animal", id));
    }

    private static String texto(Enum<?> valor) {
        return valor.name().toLowerCase();
    }
}
