package com.innovax.ganadera.evento;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.Rodeo;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.common.dto.PaginaResponse;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.evento.dto.EventoFiltro;
import com.innovax.ganadera.evento.dto.EventoRequest;
import com.innovax.ganadera.evento.dto.EventoResponse;
import com.innovax.ganadera.evento.dto.OperacionResponse;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Registro, consulta y anulación de eventos de cualquier tipo. Las validaciones comunes están acá;
 * las de cada tipo, en su {@link TipoEventoHandler}.
 */
@Service
public class EventoService {

    private final EventoRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final AnimalRepository animalRepository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacion;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Map<TipoEvento, TipoEventoHandler<?>> handlers = new EnumMap<>(TipoEvento.class);

    public EventoService(EventoRepository repository, EstablecimientoRepository establecimientoRepository,
                         AnimalRepository animalRepository, LoteRepository loteRepository,
                         RodeoRepository rodeoRepository, UsuarioRepository usuarioRepository, AutorizacionService autorizacion,
                         ObjectMapper objectMapper, Validator validator, List<TipoEventoHandler<?>> handlers) {
        this.repository = repository;
        this.establecimientoRepository = establecimientoRepository;
        this.animalRepository = animalRepository;
        this.loteRepository = loteRepository;
        this.rodeoRepository = rodeoRepository;
        this.usuarioRepository = usuarioRepository;
        this.autorizacion = autorizacion;
        this.objectMapper = objectMapper;
        this.validator = validator;
        for (TipoEventoHandler<?> handler : handlers) {
            if (this.handlers.put(handler.tipo(), handler) != null) {
                throw new IllegalStateException("Hay dos handlers para el tipo " + handler.tipo());
            }
        }
    }

    // ---------- Consultas ----------

    /** Más recientes primero. Con filtro.animalId() es el historial del animal. */
    @Transactional(readOnly = true)
    public PaginaResponse<EventoResponse> listar(UUID establecimientoId, EventoFiltro filtro) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoActivo(establecimientoId);
        var orden = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("createdAt"));
        var pageable = PageRequest.of(filtro.paginaValida(), filtro.tamanioValido(), orden);
        // Un animal que cambió de campo conserva su historia: se ve también lo cargado en los anteriores
        boolean historialDelAnimal = filtro.animalId() != null
            && animalRepository.existsByIdAndEstablecimientoId(filtro.animalId(), establecimientoId);
        return PaginaResponse.de(
            repository.findAll(EventoSpecs.de(establecimientoId, filtro, historialDelAnimal), pageable),
            this::aRespuesta);
    }

    @Transactional(readOnly = true)
    public EventoResponse obtener(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        return aRespuesta(buscar(establecimientoId, id));
    }

    // ---------- Registro ----------

    @Transactional
    public OperacionResponse registrar(UUID establecimientoId, EventoRequest request) {
        exigirPermisoDelTipo(establecimientoId, request.tipo());
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);

        // Reintento de una operación ya registrada (ej: app offline): se devuelve lo que ya existe
        if (request.operacionId() != null && repository.existsByOperacionId(request.operacionId())) {
            return operacionExistente(establecimientoId, request.operacionId());
        }

        TipoEventoHandler<?> handler = handlers.get(request.tipo());
        if (handler == null) {
            throw new BusinessException("El evento " + request.tipo().etiqueta() + " todavía no está disponible");
        }
        return registrarCon(handler, establecimiento, request);
    }

    /** Separado para que el tipo D quede fijo y no haya que castear los datos. */
    private <D> OperacionResponse registrarCon(TipoEventoHandler<D> handler, Establecimiento establecimiento,
                                               EventoRequest request) {
        D datosEnviados = leerDatos(handler, request.datos());
        List<Animal> animales = animales(establecimiento.getId(), request.animalIdsONada());
        Lote lote = lote(establecimiento.getId(), request.loteId());
        Rodeo rodeo = rodeo(establecimiento.getId(), request.rodeoId());

        if (!animales.isEmpty() && lote != null) {
            throw new BusinessException("Indicá animales o un lote, no las dos cosas");
        }
        if (!animales.isEmpty() && rodeo != null) {
            throw new BusinessException("Indicá animales o un rodeo, no las dos cosas");
        }
        if (lote != null && rodeo != null) {
            throw new BusinessException("Indicá un lote o un rodeo, no los dos");
        }
        if (rodeo != null && !request.tipo().requiereAnimales()) {
            throw new BusinessException("El evento " + request.tipo().etiqueta() + " no se puede registrar sobre un rodeo");
        }
        if (request.tipo().requiereAnimales() && animales.isEmpty() && (lote != null || rodeo != null)) {
            // Carga por lote o rodeo: un evento por cada animal activo del grupo
            animales = lote != null
                ? animalRepository.findByEstablecimientoIdAndLoteIdAndEstadoOrderByCaravana(
                    establecimiento.getId(), lote.getId(), EstadoAnimal.ACTIVO)
                : animalRepository.findByEstablecimientoIdAndRodeoIdAndEstadoOrderByCaravana(
                    establecimiento.getId(), rodeo.getId(), EstadoAnimal.ACTIVO);
            String grupo = lote != null ? "El lote " + lote.getNombre() : "El rodeo " + rodeo.getNombre();
            if (animales.isEmpty()) {
                throw new BusinessException(grupo + " no tiene animales");
            }
            // Solo los del grupo a los que aplica el tipo (ej: el servicio, a las vacas y vaquillonas)
            animales = animales.stream().filter(handler::aplicaA).toList();
            if (animales.isEmpty()) {
                throw new BusinessException(grupo + " no tiene animales a los que se pueda registrar un "
                    + request.tipo().etiqueta().toLowerCase());
            }
            lote = null;
        }
        if (request.tipo().requiereAnimales() && animales.isEmpty()) {
            throw new BusinessException("Elegí al menos un animal");
        }
        for (Animal animal : animales) {
            if (animal.getFechaNacimiento() != null && request.fecha().isBefore(animal.getFechaNacimiento())) {
                throw new BusinessException("La fecha es anterior al nacimiento de " + animal.getCaravana());
            }
        }
        var contexto = new TipoEventoHandler.Contexto(establecimiento, request.fecha(), animales, lote);
        handler.validar(datosEnviados, contexto);
        D datos = handler.completar(datosEnviados, contexto);

        UUID operacionId = request.operacionId() != null ? request.operacionId() : UUID.randomUUID();
        Usuario usuario = usuarioRepository.getReferenceById(TenantContext.requerirUsuario().userId());

        List<Evento> eventos = new ArrayList<>();
        List<D> datosGuardados = new ArrayList<>();
        if (animales.isEmpty()) {
            eventos.add(new Evento(establecimiento, operacionId, request.tipo(), request.fecha(), null, lote,
                escribir(datos), usuario));
            datosGuardados.add(datos);
        } else {
            for (Animal animal : animales) {
                // Cada animal puede tener datos propios (ej: de qué lote sale en un movimiento)
                D datosDelAnimal = handler.completarPorAnimal(datos, animal, contexto);
                datosGuardados.add(datosDelAnimal);
                eventos.add(new Evento(establecimiento, operacionId, request.tipo(), request.fecha(), animal, null,
                    escribir(datosDelAnimal), usuario));
            }
        }
        repository.saveAll(eventos);
        for (int i = 0; i < eventos.size(); i++) {
            handler.aplicar(eventos.get(i), datosGuardados.get(i));
        }
        repository.flush();

        return new OperacionResponse(operacionId, eventos.size(),
            repository.findByOperacionIdOrderByCreatedAtAsc(operacionId).stream().map(this::aRespuesta).toList());
    }

    /** Un animal y los datos propios que se le registran (ej: su peso en una grilla de pesajes). */
    public record ItemPorAnimal(UUID animalId, JsonNode datos) {
    }

    /**
     * Registra el mismo tipo de evento en varios animales, cada uno con sus propios datos, todo o
     * nada y como una sola operación. Es lo que usa una carga en grilla (ej: pesajes caravana por
     * caravana). Las mismas validaciones y efectos que {@link #registrar}, animal por animal.
     */
    @Transactional
    public OperacionResponse registrarPorAnimal(UUID establecimientoId, TipoEvento tipo, UUID operacionId,
                                                LocalDate fecha, List<ItemPorAnimal> items) {
        exigirPermisoDelTipo(establecimientoId, tipo);
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);

        if (operacionId != null && repository.existsByOperacionId(operacionId)) {
            return operacionExistente(establecimientoId, operacionId);
        }
        TipoEventoHandler<?> handler = handlers.get(tipo);
        if (handler == null) {
            throw new BusinessException("El evento " + tipo.etiqueta() + " todavía no está disponible");
        }
        if (items.isEmpty()) {
            throw new BusinessException("No hay nada para registrar");
        }
        return registrarItemsCon(handler, establecimiento, tipo, operacionId, fecha, items);
    }

    private <D> OperacionResponse registrarItemsCon(TipoEventoHandler<D> handler, Establecimiento establecimiento,
                                                    TipoEvento tipo, UUID operacionId, LocalDate fecha,
                                                    List<ItemPorAnimal> items) {
        List<UUID> ids = items.stream().map(ItemPorAnimal::animalId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException("Hay animales repetidos");
        }
        Map<UUID, Animal> animales = animales(establecimiento.getId(), ids).stream()
            .collect(Collectors.toMap(Animal::getId, Function.identity()));

        UUID operacion = operacionId != null ? operacionId : UUID.randomUUID();
        Usuario usuario = usuarioRepository.getReferenceById(TenantContext.requerirUsuario().userId());
        List<Evento> eventos = new ArrayList<>();
        List<D> datosGuardados = new ArrayList<>();
        for (ItemPorAnimal item : items) {
            Animal animal = animales.get(item.animalId());
            if (animal.getFechaNacimiento() != null && fecha.isBefore(animal.getFechaNacimiento())) {
                throw new BusinessException("La fecha es anterior al nacimiento de " + animal.getCaravana());
            }
            D enviados = leerDatos(handler, item.datos());
            var contexto = new TipoEventoHandler.Contexto(establecimiento, fecha, List.of(animal), null);
            handler.validar(enviados, contexto);
            D datos = handler.completarPorAnimal(handler.completar(enviados, contexto), animal, contexto);
            datosGuardados.add(datos);
            eventos.add(new Evento(establecimiento, operacion, tipo, fecha, animal, null, escribir(datos), usuario));
        }
        repository.saveAll(eventos);
        for (int i = 0; i < eventos.size(); i++) {
            handler.aplicar(eventos.get(i), datosGuardados.get(i));
        }
        repository.flush();

        return new OperacionResponse(operacion, eventos.size(),
            repository.findByOperacionIdOrderByCreatedAtAsc(operacion).stream().map(this::aRespuesta).toList());
    }

    // ---------- Anulación ----------

    /** Los eventos no se borran: se anulan y salen del historial. Si el evento cambió algo, se deshace. */
    @Transactional
    public void anular(UUID establecimientoId, UUID id) {
        Evento evento = buscar(establecimientoId, id);
        exigirPermisoDelTipo(establecimientoId, evento.getTipo());
        TipoEventoHandler<?> handler = handlers.get(evento.getTipo());
        if (handler != null) {
            revertirCon(handler, evento);
        }
        evento.anular();
    }

    private <D> void revertirCon(TipoEventoHandler<D> handler, Evento evento) {
        try {
            handler.revertir(evento, objectMapper.readValue(evento.getDatos(), handler.claseDatos()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Datos guardados ilegibles en el evento " + evento.getId(), e);
        }
    }

    // ---------- Validaciones comunes ----------

    private void exigirPermisoDelTipo(UUID establecimientoId, TipoEvento tipo) {
        Rol rol = autorizacion.rolEn(establecimientoId);
        if (!tipo.permitidoPara(rol)) {
            throw new AccessDeniedException("El rol " + rol + " no puede registrar " + tipo);
        }
    }

    /** Convierte el JSON de datos en el record del tipo y lo valida. Los errores salen como "datos.campo". */
    private <D> D leerDatos(TipoEventoHandler<D> handler, JsonNode json) {
        D datos;
        try {
            datos = objectMapper.convertValue(json == null ? objectMapper.createObjectNode() : json,
                handler.claseDatos());
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("datos", "Los datos no tienen el formato esperado")));
        }
        Set<ConstraintViolation<D>> errores = validator.validate(datos);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores.stream()
                .map(v -> new ApiError.FieldError("datos." + v.getPropertyPath(), v.getMessage()))
                .toList());
        }
        return datos;
    }

    private String escribir(Object datos) {
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron guardar los datos del evento", e);
        }
    }

    /** Animales activos del establecimiento. Si alguno no lo es, no se registra nada. */
    private List<Animal> animales(UUID establecimientoId, List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        Set<UUID> unicos = new HashSet<>(ids);
        List<Animal> animales = animalRepository.findAllById(unicos).stream()
            .filter(a -> a.getEstablecimiento().getId().equals(establecimientoId) && a.isActivo())
            .toList();
        if (animales.size() != unicos.size()) {
            throw new BusinessException("Alguno de los animales elegidos no está en este establecimiento");
        }
        return animales;
    }

    private Lote lote(UUID establecimientoId, UUID loteId) {
        if (loteId == null) return null;
        return loteRepository.findByIdAndEstablecimientoIdAndActivoTrue(loteId, establecimientoId)
            .orElseThrow(() -> new BusinessException("El lote elegido no existe en este establecimiento"));
    }

    private Rodeo rodeo(UUID establecimientoId, UUID rodeoId) {
        if (rodeoId == null) return null;
        return rodeoRepository.findByIdAndEstablecimientoIdAndActivoTrue(rodeoId, establecimientoId)
            .orElseThrow(() -> new BusinessException("El rodeo elegido no existe en este establecimiento"));
    }

    private Establecimiento establecimientoActivo(UUID id) {
        return establecimientoRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    private Evento buscar(UUID establecimientoId, UUID id) {
        establecimientoActivo(establecimientoId);
        return repository.findByIdAndEstablecimientoIdAndDeletedAtIsNull(id, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Evento", id));
    }

    private OperacionResponse operacionExistente(UUID establecimientoId, UUID operacionId) {
        List<Evento> eventos = repository.findByOperacionIdOrderByCreatedAtAsc(operacionId).stream()
            .filter(e -> e.getEstablecimiento().getId().equals(establecimientoId))
            .toList();
        if (eventos.isEmpty()) {
            // El id ya se usó en otro establecimiento de la organización
            throw new BusinessException("Ese identificador de operación ya está en uso");
        }
        return new OperacionResponse(operacionId, eventos.size(), eventos.stream().map(this::aRespuesta).toList());
    }

    private EventoResponse aRespuesta(Evento e) {
        Usuario u = e.getUsuario();
        String nombreUsuario = u.getApellido() == null ? u.getNombre() : u.getNombre() + " " + u.getApellido();
        return new EventoResponse(
            e.getId(),
            e.getOperacionId(),
            new EventoResponse.Referencia(e.getEstablecimiento().getId(), e.getEstablecimiento().getNombre()),
            e.getTipo(),
            e.getFecha(),
            e.getAnimal() == null ? null : new EventoResponse.Referencia(e.getAnimal().getId(), e.getAnimal().getCaravana()),
            e.getLote() == null ? null : new EventoResponse.Referencia(e.getLote().getId(), e.getLote().getNombre()),
            leerJson(e.getDatos()),
            new EventoResponse.Referencia(u.getId(), nombreUsuario),
            e.getCreatedAt());
    }

    private JsonNode leerJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Datos de evento ilegibles", ex);
        }
    }
}
