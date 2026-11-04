package com.innovax.ganadera.sync;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.innovax.ganadera.animal.AnimalService;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.evento.EventoService;
import com.innovax.ganadera.evento.dto.EventoRequest;
import com.innovax.ganadera.sync.dto.OperacionSync;
import com.innovax.ganadera.sync.dto.ResultadoOperacion;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

/**
 * Push de la sincronización (#30): aplica lo que se hizo en el celular, en orden, cada operación en
 * su propia transacción (la de cada servicio). Si una falla, las demás se aplican igual.
 *
 * <p>Todas son idempotentes: si el celular no recibió la respuesta y reenvía, no se repiten. Los
 * eventos usan el id de la operación como {@code operacionId}; anular uno ya anulado no hace nada; y
 * una edición repetida pierde contra sí misma por last-write-wins.
 *
 * <p>Sin {@code @Transactional} a propósito: cada operación tiene que poder fallar sola.
 */
@Slf4j
@Service
class SyncPushService {

    private final EventoService eventoService;
    private final EventoRepository eventoRepository;
    private final AnimalService animalService;
    private final Validator validator;
    private final Clock reloj;

    SyncPushService(EventoService eventoService, EventoRepository eventoRepository, AnimalService animalService,
                    Validator validator, Clock reloj) {
        this.eventoService = eventoService;
        this.eventoRepository = eventoRepository;
        this.animalService = animalService;
        this.validator = validator;
        this.reloj = reloj;
    }

    List<ResultadoOperacion> aplicar(UUID establecimientoId, List<OperacionSync> operaciones) {
        List<ResultadoOperacion> resultados = new ArrayList<>();
        for (OperacionSync op : operaciones) {
            resultados.add(aplicarUna(establecimientoId, op));
        }
        return resultados;
    }

    private ResultadoOperacion aplicarUna(UUID est, OperacionSync op) {
        try {
            return switch (op.tipo()) {
                case REGISTRAR_EVENTO -> registrarEvento(est, op);
                case REGISTRAR_EVENTOS_POR_ANIMAL -> registrarPorAnimal(est, op);
                case ANULAR_EVENTO -> anularEvento(est, op);
                case ACTUALIZAR_ANIMAL -> actualizarAnimal(est, op);
            };
        } catch (DatosInvalidosException e) {
            return ResultadoOperacion.rechazado(op.id(), e.getMessage(), e.getFieldErrors());
        } catch (BusinessException | ConflictException | NotFoundException e) {
            return ResultadoOperacion.rechazado(op.id(), e.getMessage(), null);
        } catch (AccessDeniedException e) {
            return ResultadoOperacion.rechazado(op.id(), "No tenés permiso para hacer esto en este establecimiento", null);
        } catch (RuntimeException e) {
            log.error("Sync: falló la operación {} ({}) en {}", op.id(), op.tipo(), est, e);
            return ResultadoOperacion.error(op.id(), "No se pudo procesar: se va a reintentar");
        }
    }

    private ResultadoOperacion registrarEvento(UUID est, OperacionSync op) {
        EventoRequest e = op.evento();
        if (e == null) throw new BusinessException("Falta el evento");
        // El id de la operación es el operacionId: un reenvío no duplica el evento
        EventoRequest request = new EventoRequest(op.id(), e.tipo(), e.fecha(), e.animalIds(), e.loteId(),
            e.rodeoId(), e.datos());
        validar(request);
        return ResultadoOperacion.evento(op.id(), eventoService.registrar(est, request).operacionId());
    }

    private ResultadoOperacion registrarPorAnimal(UUID est, OperacionSync op) {
        List<ApiError.FieldError> errores = new ArrayList<>();
        if (op.eventoTipo() == null) errores.add(new ApiError.FieldError("eventoTipo", "Elegí el tipo de evento"));
        if (op.fecha() == null) errores.add(new ApiError.FieldError("fecha", "Indicá la fecha"));
        else if (op.fecha().isAfter(LocalDate.now(reloj))) {
            errores.add(new ApiError.FieldError("fecha", "La fecha no puede ser futura"));
        }
        if (op.items() == null || op.items().isEmpty()) errores.add(new ApiError.FieldError("items", "No hay nada para registrar"));
        else if (op.items().size() > 500) errores.add(new ApiError.FieldError("items", "Se pueden registrar hasta 500 animales por vez"));
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);

        var items = op.items().stream()
            .map(i -> new EventoService.ItemPorAnimal(i.animalId(), i.datos()))
            .toList();
        return ResultadoOperacion.evento(op.id(),
            eventoService.registrarPorAnimal(est, op.eventoTipo(), op.id(), op.fecha(), items).operacionId());
    }

    private ResultadoOperacion anularEvento(UUID est, OperacionSync op) {
        if (op.eventoId() == null) throw new BusinessException("Falta el evento a anular");
        Optional<Evento> evento = eventoRepository.findById(op.eventoId())
            .filter(e -> e.getEstablecimiento().getId().equals(est));
        if (evento.isEmpty()) throw new BusinessException("El evento ya no existe");
        // Anular dos veces es lo mismo que una
        if (!evento.get().isAnulado()) eventoService.anular(est, op.eventoId());
        return ResultadoOperacion.aplicado(op.id());
    }

    private ResultadoOperacion actualizarAnimal(UUID est, OperacionSync op) {
        if (op.animalId() == null) throw new BusinessException("Falta el animal");
        var resultado = animalService.aplicarCambiosSincronizados(est, op.animalId(), op.cambios(), op.modificadoEn());
        return ResultadoOperacion.campos(op.id(), resultado.aplicados(), resultado.descartados());
    }

    private void validar(EventoRequest request) {
        Set<ConstraintViolation<EventoRequest>> violaciones = validator.validate(request);
        if (!violaciones.isEmpty()) {
            throw new DatosInvalidosException(violaciones.stream()
                .map(v -> new ApiError.FieldError("evento." + v.getPropertyPath(), v.getMessage()))
                .toList());
        }
    }
}
