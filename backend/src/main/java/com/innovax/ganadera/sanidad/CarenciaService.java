package com.innovax.ganadera.sanidad;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.tipos.TratamientoDatos;
import com.innovax.ganadera.sanidad.dto.CarenciaResponse;

/**
 * Dice si un animal está en período de carencia, a partir de sus tratamientos (issue #20).
 *
 * <p>Regla: un tratamiento con N días de carencia aplicado el día F deja al animal en carencia
 * hasta el día F + N, que es cuando queda libre ({@code libreDesde}); ese día ya se puede faenar o
 * vender. Con 0 días no hay carencia. Los tratamientos anulados no cuentan. El "hoy" es el de
 * Argentina (el {@link Clock} de la aplicación).
 *
 * <p>Configurable: {@code app.sanidad.carencia-dias-extra} suma días a todas las carencias (por defecto 0).
 *
 * <p>Pensado para que lo use también la venta/baja (#23) para advertir o impedir la salida de
 * animales en carencia.
 */
@Service
public class CarenciaService {

    private final EventoRepository eventoRepository;
    private final ObjectMapper objectMapper;
    private final Clock reloj;
    private final int diasExtra;

    public CarenciaService(EventoRepository eventoRepository, ObjectMapper objectMapper, Clock reloj,
                           @Value("${app.sanidad.carencia-dias-extra:0}") int diasExtra) {
        this.eventoRepository = eventoRepository;
        this.objectMapper = objectMapper;
        this.reloj = reloj;
        this.diasExtra = Math.max(0, diasExtra);
    }

    /** Carencia vigente de cada animal que la tenga. Los que no, no aparecen en el mapa. Una sola consulta. */
    @Transactional(readOnly = true)
    public Map<UUID, CarenciaResponse> deAnimales(UUID establecimientoId, Collection<UUID> animalIds) {
        if (animalIds.isEmpty()) return Map.of();
        LocalDate hoy = LocalDate.now(reloj);
        // Con el máximo de carencia, un tratamiento más viejo que esto ya no puede estar vigente
        LocalDate desde = hoy.minusDays(TratamientoDatos.MAX_DIAS_CARENCIA);

        Map<UUID, List<CarenciaResponse.Tratamiento>> porAnimal = new HashMap<>();
        for (Evento e : eventoRepository.buscarDeAnimalesDesde(establecimientoId, TipoEvento.TRATAMIENTO, animalIds, desde)) {
            TratamientoDatos datos = leer(e);
            int dias = datos.diasCarencia() == null ? 0 : datos.diasCarencia();
            LocalDate libreDesde = e.getFecha().plusDays(dias + diasExtra);
            if (dias > 0 && libreDesde.isAfter(hoy)) {
                porAnimal.computeIfAbsent(e.getAnimal().getId(), k -> new ArrayList<>())
                    .add(new CarenciaResponse.Tratamiento(datos.producto(), e.getFecha(), dias, libreDesde));
            }
        }

        Map<UUID, CarenciaResponse> resultado = new HashMap<>();
        porAnimal.forEach((animalId, tratamientos) -> {
            tratamientos.sort(Comparator.comparing(CarenciaResponse.Tratamiento::libreDesde).reversed());
            resultado.put(animalId, new CarenciaResponse(tratamientos.get(0).libreDesde(), List.copyOf(tratamientos)));
        });
        return resultado;
    }

    private TratamientoDatos leer(Evento evento) {
        try {
            return objectMapper.readValue(evento.getDatos(), TratamientoDatos.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Datos de tratamiento ilegibles en el evento " + evento.getId(), ex);
        }
    }
}
