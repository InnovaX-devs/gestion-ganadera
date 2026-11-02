package com.innovax.ganadera.reproduccion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.tipos.ServicioDatos;
import com.innovax.ganadera.evento.tipos.TactoDatos;
import com.innovax.ganadera.evento.tipos.TipoServicio;
import com.innovax.ganadera.reproduccion.dto.EstadoReproductivoResponse;

/**
 * Estado reproductivo de vacas y vaquillonas (#19), a partir de sus eventos. Manda el último
 * servicio, tacto o nacimiento vigente (por fecha y, el mismo día, el último cargado):
 *
 * <ul>
 *   <li>servicio → en servicio (natural sin fin o con el fin por delante) o servida;</li>
 *   <li>tacto → preñada (con la fecha estimada de parto) o vacía;</li>
 *   <li>nacimiento → parida.</li>
 * </ul>
 *
 * Como no se guarda en el animal, anular un evento lo corrige solo. Igual que la carencia, cuenta
 * eventos de cualquier establecimiento: si el animal se movió de campo (#22), su historia lo sigue.
 */
@Service
public class EstadoReproductivoService {

    private static final Set<TipoEvento> TIPOS = Set.of(TipoEvento.SERVICIO, TipoEvento.TACTO, TipoEvento.NACIMIENTO);

    private static final Comparator<Evento> CRONOLOGICO =
        Comparator.comparing(Evento::getFecha).thenComparing(Evento::getCreatedAt);

    private final EventoRepository eventoRepository;
    private final ObjectMapper objectMapper;
    private final Clock reloj;

    public EstadoReproductivoService(EventoRepository eventoRepository, ObjectMapper objectMapper, Clock reloj) {
        this.eventoRepository = eventoRepository;
        this.objectMapper = objectMapper;
        this.reloj = reloj;
    }

    /** Solo las vacas y vaquillonas tienen estado reproductivo: el resto no aparece en el mapa. Una sola consulta. */
    @Transactional(readOnly = true)
    public Map<UUID, EstadoReproductivoResponse> deAnimales(Collection<Animal> animales) {
        List<UUID> ids = animales.stream().filter(EstadoReproductivoService::aplica).map(Animal::getId).toList();
        if (ids.isEmpty()) return Map.of();

        Map<UUID, List<Evento>> porAnimal = eventoRepository.buscarDeAnimales(TIPOS, ids).stream()
            .collect(Collectors.groupingBy(e -> e.getAnimal().getId()));
        LocalDate hoy = LocalDate.now(reloj);
        Map<UUID, EstadoReproductivoResponse> resultado = new HashMap<>();
        for (UUID id : ids) {
            resultado.put(id, calcular(porAnimal.getOrDefault(id, List.of()), hoy));
        }
        return resultado;
    }

    public static boolean aplica(Animal animal) {
        return animal.getCategoria() == Categoria.VACA || animal.getCategoria() == Categoria.VAQUILLONA;
    }

    private EstadoReproductivoResponse calcular(List<Evento> eventos, LocalDate hoy) {
        if (eventos.isEmpty()) return EstadoReproductivoResponse.SIN_DATOS;

        Evento servicio = ultimo(eventos, TipoEvento.SERVICIO);
        Evento tacto = ultimo(eventos, TipoEvento.TACTO);
        Evento parto = ultimo(eventos, TipoEvento.NACIMIENTO);
        ServicioDatos datosServicio = servicio == null ? null : leer(servicio, ServicioDatos.class);
        TactoDatos datosTacto = tacto == null ? null : leer(tacto, TactoDatos.class);

        Evento manda = eventos.stream().max(CRONOLOGICO).orElseThrow();
        EstadoReproductivo estado = switch (manda.getTipo()) {
            case SERVICIO -> enCurso(datosServicio, hoy) ? EstadoReproductivo.EN_SERVICIO : EstadoReproductivo.SERVIDA;
            case TACTO -> datosTacto.prenada() ? EstadoReproductivo.PRENADA : EstadoReproductivo.VACIA;
            default -> EstadoReproductivo.PARIDA;
        };

        return new EstadoReproductivoResponse(
            estado,
            manda.getFecha(),
            estado == EstadoReproductivo.PRENADA ? datosTacto.fechaEstimadaParto() : null,
            servicio == null ? null : new EstadoReproductivoResponse.Servicio(
                servicio.getFecha(), datosServicio.fechaFin(), datosServicio.tipo(),
                datosServicio.toros() == null || datosServicio.toros().isEmpty() ? null
                    : datosServicio.toros().stream().map(ServicioDatos.Toro::caravana).toList(),
                datosServicio.pajuela()),
            tacto == null ? null : new EstadoReproductivoResponse.Tacto(
                tacto.getFecha(), datosTacto.resultado(), datosTacto.mesesGestacion(), datosTacto.veterinario(),
                datosTacto.fechaEstimadaParto()),
            parto == null ? null : parto.getFecha());
    }

    /** Un servicio natural está en curso mientras no termine; una inseminación es en el día. */
    private static boolean enCurso(ServicioDatos datos, LocalDate hoy) {
        return datos.tipo() == TipoServicio.NATURAL && (datos.fechaFin() == null || !datos.fechaFin().isBefore(hoy));
    }

    private static Evento ultimo(List<Evento> eventos, TipoEvento tipo) {
        return eventos.stream().filter(e -> e.getTipo() == tipo).max(CRONOLOGICO).orElse(null);
    }

    private <T> T leer(Evento evento, Class<T> clase) {
        try {
            return objectMapper.readValue(evento.getDatos(), clase);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Datos ilegibles en el evento " + evento.getId(), ex);
        }
    }
}
