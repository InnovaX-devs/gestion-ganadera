package com.innovax.ganadera.pesaje;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.common.dto.PaginaResponse;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.evento.EventoService;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.dto.EventoFiltro;
import com.innovax.ganadera.evento.dto.EventoResponse;
import com.innovax.ganadera.evento.dto.OperacionResponse;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.pesaje.dto.PesajeGrillaRequest;
import com.innovax.ganadera.pesaje.dto.PesajeResponse;

import lombok.RequiredArgsConstructor;

/**
 * Pesajes (#21) por encima del modelo genérico de eventos:
 * <ul>
 *   <li>La carga en grilla (caravana + peso por fila, todo o nada).</li>
 *   <li>El listado con la ganancia diaria de peso respecto al pesaje anterior del mismo animal.</li>
 * </ul>
 * El pesaje individual y el grupal (peso total / cantidad) se registran con
 * {@code POST /eventos} y tipo PESAJE; ver {@code PesajeHandler}.
 */
@Service
@RequiredArgsConstructor
public class PesajeService {

    /** Desde cuándo se buscan pesajes anteriores: antes de cualquier fecha posible. */
    private static final LocalDate DESDE_SIEMPRE = LocalDate.of(1900, 1, 1);

    private final EventoService eventoService;
    private final EventoRepository eventoRepository;
    private final AnimalRepository animalRepository;
    private final AutorizacionService autorizacion;
    private final ObjectMapper objectMapper;

    // ---------- Carga en grilla ----------

    /**
     * Registra un pesaje por fila. Si alguna caravana no existe (o es de un animal que no está
     * activo), está repetida o el peso es inválido, no se guarda ninguna fila y el error indica cuáles.
     */
    @Transactional
    public OperacionResponse registrarGrilla(UUID establecimientoId, PesajeGrillaRequest request) {
        // Antes de mirar caravanas: quien no puede pesar no tiene por qué saber cuáles existen
        Rol rol = autorizacion.rolEn(establecimientoId);
        if (!TipoEvento.PESAJE.permitidoPara(rol)) {
            throw new AccessDeniedException("El rol " + rol + " no puede registrar " + TipoEvento.PESAJE);
        }

        List<PesajeGrillaRequest.Fila> filas = request.pesajes();
        List<String> caravanas = filas.stream().map(f -> Animal.normalizarCaravana(f.caravana())).toList();
        Map<String, Animal> animales = new HashMap<>();
        animalRepository.findByEstablecimientoIdAndCaravanaInAndEstado(
            establecimientoId, caravanas, EstadoAnimal.ACTIVO).forEach(a -> animales.put(a.getCaravana(), a));

        List<ApiError.FieldError> errores = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        List<EventoService.ItemPorAnimal> items = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            String caravana = caravanas.get(i);
            Animal animal = animales.get(caravana);
            if (animal == null) {
                errores.add(new ApiError.FieldError("pesajes[" + i + "].caravana",
                    "No hay un animal activo con la caravana " + caravana));
            } else if (!vistas.add(caravana)) {
                errores.add(new ApiError.FieldError("pesajes[" + i + "].caravana",
                    "La caravana " + caravana + " está repetida"));
            } else {
                items.add(new EventoService.ItemPorAnimal(animal.getId(),
                    objectMapper.createObjectNode().put("kg", filas.get(i).kg())));
            }
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return eventoService.registrarPorAnimal(
            establecimientoId, TipoEvento.PESAJE, request.operacionId(), request.fecha(), items);
    }

    // ---------- Listado con ganancia de peso ----------

    /**
     * Pesajes, los más recientes primero. Con {@code animalId} es la curva de peso del animal. Cada
     * pesaje trae su variación respecto al anterior del mismo animal (el último anterior por fecha,
     * sin contar los anulados), aunque ese anterior caiga en otra página.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<PesajeResponse> listar(UUID establecimientoId, EventoFiltro filtro) {
        var pagina = eventoService.listar(establecimientoId, new EventoFiltro(TipoEvento.PESAJE,
            filtro.animalId(), null, filtro.desde(), filtro.hasta(), filtro.pagina(), filtro.tamanio()));

        List<UUID> animalIds = pagina.contenido().stream()
            .filter(e -> e.animal() != null).map(e -> e.animal().id()).distinct().toList();
        Map<UUID, List<Medicion>> historial = historialDe(establecimientoId, animalIds);

        return new PaginaResponse<>(
            pagina.contenido().stream().map(e -> aRespuesta(e, historial)).toList(),
            pagina.total(), pagina.pagina(), pagina.tamanio(), pagina.totalPaginas());
    }

    /** Todos los pesajes vigentes de esos animales, del más viejo al más nuevo. */
    private Map<UUID, List<Medicion>> historialDe(UUID establecimientoId, List<UUID> animalIds) {
        Map<UUID, List<Medicion>> porAnimal = new HashMap<>();
        if (animalIds.isEmpty()) return porAnimal;
        for (Evento e : eventoRepository.buscarDeAnimalesDesde(
                establecimientoId, TipoEvento.PESAJE, animalIds, DESDE_SIEMPRE)) {
            porAnimal.computeIfAbsent(e.getAnimal().getId(), k -> new ArrayList<>())
                .add(new Medicion(e.getId(), e.getFecha(), kgDe(leer(e.getDatos())), e.getCreatedAt()));
        }
        porAnimal.values().forEach(l -> l.sort(
            Comparator.comparing(Medicion::fecha).thenComparing(Medicion::creado)));
        return porAnimal;
    }

    private PesajeResponse aRespuesta(EventoResponse e, Map<UUID, List<Medicion>> historial) {
        JsonNode datos = e.datos();
        BigDecimal kg = kgDe(datos);

        Medicion anterior = null;
        if (e.animal() != null) {
            for (Medicion m : historial.getOrDefault(e.animal().id(), List.of())) {
                if (m.fecha().isBefore(e.fecha())) anterior = m; // la lista viene ordenada: queda la más reciente
            }
        }
        BigDecimal variacion = null;
        Integer dias = null;
        BigDecimal ganancia = null;
        if (anterior != null) {
            variacion = kg.subtract(anterior.kg()).setScale(2, RoundingMode.HALF_UP);
            dias = (int) ChronoUnit.DAYS.between(anterior.fecha(), e.fecha());
            ganancia = variacion.divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);
        }
        return new PesajeResponse(
            e.id(), e.operacionId(), e.fecha(), e.animal(), kg,
            datos.path("grupal").asBoolean(false),
            datos.hasNonNull("pesoTotal") ? datos.get("pesoTotal").decimalValue() : null,
            datos.hasNonNull("cantidad") ? datos.get("cantidad").asInt() : null,
            anterior == null ? null : anterior.fecha(),
            anterior == null ? null : anterior.kg(),
            variacion, dias, ganancia, e.registradoPor());
    }

    private JsonNode leer(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Datos de pesaje ilegibles", ex);
        }
    }

    private static BigDecimal kgDe(JsonNode datos) {
        return datos.get("kg").decimalValue();
    }

    private record Medicion(UUID id, LocalDate fecha, BigDecimal kg, java.time.Instant creado) {
    }
}
