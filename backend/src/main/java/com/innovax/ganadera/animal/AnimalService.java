package com.innovax.ganadera.animal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.Rodeo;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.animal.categoria.CategoriaService;
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
import com.innovax.ganadera.evento.EventoService;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.dto.EventoRequest;
import com.innovax.ganadera.evento.tipos.MovimientoDatos;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.sanidad.CarenciaService;
import com.innovax.ganadera.sanidad.dto.CarenciaResponse;

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
    private final CategoriaService categoriaService;
    private final EventoRepository eventoRepository;
    private final CarenciaService carenciaService;
    private final EventoService eventoService;
    private final ObjectMapper objectMapper;
    private final Clock reloj;

    /** Listado paginado con filtros (#14). Ordenado por caravana. */
    @Transactional(readOnly = true)
    public PaginaResponse<AnimalResponse> listar(UUID establecimientoId, AnimalFiltro filtro) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoActivo(establecimientoId);
        var pageable = PageRequest.of(filtro.paginaValida(), filtro.tamanioValido(), Sort.by("caravana"));
        var pagina = repository.findAll(AnimalSpecs.de(establecimientoId, filtro), pageable);
        var carencias = carenciaService.deAnimales(establecimientoId, pagina.map(Animal::getId).getContent());
        return PaginaResponse.de(pagina, a -> AnimalResponse.from(a, carencias.get(a.getId())));
    }

    /**
     * Asigna (o quita, si destinoId es null) el mismo lote o rodeo a varios animales. Todos tienen
     * que ser activos y del establecimiento; si alguno no lo es, no se cambia ninguno.
     *
     * <p>Es un movimiento (#22) con fecha de hoy: queda en el historial de cada animal. Los que ya
     * están en el destino no cambian y no generan evento.
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

        boolean esLote = request.campo() == AsignacionRequest.Campo.LOTE;
        UUID destinoId = request.destinoId();
        List<UUID> aMover = animales.stream()
            .filter(a -> {
                var actual = esLote ? a.getLote() : a.getRodeo();
                return !Objects.equals(actual == null ? null : actual.getId(), destinoId);
            })
            .map(Animal::getId)
            .toList();
        if (aMover.isEmpty()) {
            // Nada para mover, pero un destino que no existe sigue siendo un error
            if (esLote) lote(destinoId, establecimientoId); else rodeo(destinoId, establecimientoId);
            return animales.size();
        }

        var datos = new MovimientoDatos(null,
            esLote ? destinoId : null, esLote ? null : destinoId,
            esLote && destinoId == null, !esLote && destinoId == null, null, null);
        eventoService.registrar(establecimientoId, new EventoRequest(null, TipoEvento.MOVIMIENTO,
            LocalDate.now(reloj), aMover, null, null, objectMapper.valueToTree(datos)));
        return animales.size();
    }

    @Transactional(readOnly = true)
    public AnimalResponse obtener(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        return conCarencia(establecimientoId, buscar(establecimientoId, id));
    }

    @Transactional(readOnly = true)
    public List<AnimalResponse> crias(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        buscar(establecimientoId, id);
        List<Animal> crias = repository.buscarCrias(id);
        Map<UUID, CarenciaResponse> carencias =
            carenciaService.deAnimales(establecimientoId, crias.stream().map(Animal::getId).toList());
        return crias.stream().map(a -> AnimalResponse.from(a, carencias.get(a.getId()))).toList();
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
        Categoria categoriaAnterior = animal.getCategoria();
        Map<CampoAnimal, Object> antes = valoresDeFicha(animal);
        aplicar(request, animal);
        marcarCambios(animal, antes, reloj.instant().truncatedTo(ChronoUnit.MILLIS));
        Animal guardado = repository.saveAndFlush(animal);
        categoriaService.registrarCambioManual(guardado, categoriaAnterior);
        return conCarencia(establecimientoId, guardado);
    }

    // ---------- Sincronización (#30) ----------

    /**
     * Resultado de aplicar una edición hecha sin conexión: los campos que quedaron y los que se
     * descartaron porque en el servidor había un cambio más nuevo de ese mismo campo.
     */
    public record ResultadoCambios(List<String> aplicados, List<String> descartados) {
    }

    /**
     * Aplica una edición de la ficha hecha en el celular, con last-write-wins por campo: cada campo
     * se aplica solo si {@code modificadoEn} es posterior a su última modificación en el servidor.
     * Así dos personas que editan campos distintos del mismo animal no se pisan. Un
     * {@code modificadoEn} en el futuro (reloj del celular adelantado) se toma como "ahora".
     *
     * @param cambios nombre del campo ({@link CampoAnimal#nombre()}) y su valor nuevo
     * @throws BusinessException si algún valor es inválido: no se aplica ninguno
     */
    @Transactional
    public ResultadoCambios aplicarCambiosSincronizados(UUID establecimientoId, UUID animalId,
                                                       Map<String, JsonNode> cambios, Instant modificadoEn) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_ANIMALES);
        Animal animal = buscar(establecimientoId, animalId);
        if (cambios == null || cambios.isEmpty()) {
            throw new BusinessException("No hay cambios para aplicar");
        }
        Map<CampoAnimal, JsonNode> porCampo = new EnumMap<>(CampoAnimal.class);
        cambios.forEach((nombre, valor) -> porCampo.put(
            CampoAnimal.deNombre(nombre).orElseThrow(() ->
                new BusinessException("El campo \"" + nombre + "\" no se puede editar sin conexión")),
            valor));

        // En milisegundos, como se guardan las versiones: así reenviar la misma edición no la repite
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant cuando = modificadoEn == null || modificadoEn.isAfter(ahora)
            ? ahora : modificadoEn.truncatedTo(ChronoUnit.MILLIS);
        List<String> aplicados = new ArrayList<>();
        List<String> descartados = new ArrayList<>();
        for (var entrada : porCampo.entrySet()) {
            CampoAnimal campo = entrada.getKey();
            // Empate: gana lo que ya está en el servidor
            if (!cuando.isAfter(animal.versionDe(campo))) {
                descartados.add(campo.nombre());
                continue;
            }
            asignarCampo(animal, campo, entrada.getValue());
            animal.marcarVersion(campo, cuando);
            aplicados.add(campo.nombre());
        }
        if (animal.getFechaNacimiento() == null) {
            animal.setFechaNacimientoEstimada(false);
        }
        if (aplicados.contains(CampoAnimal.CARAVANA.nombre())
                || aplicados.contains(CampoAnimal.CARAVANA_ELECTRONICA.nombre())) {
            validarCaravanas(animal);
        }
        repository.saveAndFlush(animal);
        return new ResultadoCambios(aplicados, descartados);
    }

    private static Map<CampoAnimal, Object> valoresDeFicha(Animal animal) {
        Map<CampoAnimal, Object> valores = new EnumMap<>(CampoAnimal.class);
        for (CampoAnimal campo : CampoAnimal.values()) {
            valores.put(campo, campo.valorEn(animal));
        }
        return valores;
    }

    /** Lo editado desde la web también cuenta como versión, para que el celular no lo pise con algo más viejo. */
    private static void marcarCambios(Animal animal, Map<CampoAnimal, Object> antes, Instant cuando) {
        antes.forEach((campo, valorAnterior) -> {
            if (campo.cambio(animal, valorAnterior)) animal.marcarVersion(campo, cuando);
        });
    }

    private void asignarCampo(Animal animal, CampoAnimal campo, JsonNode valor) {
        switch (campo) {
            case CARAVANA -> {
                String caravana = texto(valor, campo, 30);
                if (caravana == null) throw new BusinessException("La caravana no puede quedar vacía");
                animal.setCaravana(caravana);
            }
            case CARAVANA_ELECTRONICA -> animal.setCaravanaElectronica(texto(valor, campo, 30));
            case RAZA -> animal.setRaza(texto(valor, campo, 50));
            case OBSERVACIONES -> animal.setObservaciones(texto(valor, campo, 500));
            case FECHA_NACIMIENTO -> {
                String fecha = texto(valor, campo, 10);
                LocalDate nacimiento;
                try {
                    nacimiento = fecha == null ? null : LocalDate.parse(fecha);
                } catch (DateTimeParseException e) {
                    throw new BusinessException("La fecha de nacimiento no es válida");
                }
                if (nacimiento != null && nacimiento.isAfter(LocalDate.now(reloj))) {
                    throw new BusinessException("La fecha de nacimiento no puede ser futura");
                }
                animal.setFechaNacimiento(nacimiento);
            }
            case FECHA_NACIMIENTO_ESTIMADA -> {
                if (valor == null || !valor.isBoolean()) {
                    throw new BusinessException("fechaNacimientoEstimada tiene que ser true o false");
                }
                animal.setFechaNacimientoEstimada(valor.booleanValue());
            }
        }
    }

    /** Texto recortado; vacío o null queda en null. */
    private static String texto(JsonNode valor, CampoAnimal campo, int maximo) {
        if (valor == null || valor.isNull()) return null;
        if (!valor.isTextual()) {
            throw new BusinessException("El valor de " + campo.nombre() + " tiene que ser texto");
        }
        String limpio = valor.asText().trim();
        if (limpio.length() > maximo) {
            throw new BusinessException("Máximo " + maximo + " caracteres en " + campo.nombre());
        }
        return limpio.isEmpty() ? null : limpio;
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

    private AnimalResponse conCarencia(UUID establecimientoId, Animal animal) {
        return AnimalResponse.from(animal, carenciaService.deAnimales(establecimientoId, List.of(animal.getId()))
            .get(animal.getId()));
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
