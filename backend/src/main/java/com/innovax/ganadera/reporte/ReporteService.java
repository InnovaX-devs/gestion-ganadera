package com.innovax.ganadera.reporte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.reporte.dto.ExistenciasResponse;
import com.innovax.ganadera.reporte.dto.PesosResponse;
import com.innovax.ganadera.stock.StockService;

import lombok.RequiredArgsConstructor;

/**
 * Reportes (#32). Devuelven los datos ya calculados; la web arma con ellos la vista, el Excel y el
 * PDF. Los ve cualquier miembro del establecimiento.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    /** Período por defecto del reporte de pesos: los últimos 90 días. */
    static final int DIAS_POR_DEFECTO = 90;
    /** Período máximo: 3 años. */
    static final int DIAS_MAXIMO = 3 * 366;

    private final ReporteRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final StockService stockService;
    private final AutorizacionService autorizacion;
    private final ObjectMapper json;
    private final Clock reloj;

    // ---------- Evolución de pesos ----------

    /**
     * @param desde por defecto, 90 días antes de {@code hasta}
     * @param hasta por defecto, hoy
     * @param loteId solo animales que hoy están en ese lote
     * @param categoria solo animales que hoy son de esa categoría
     */
    @Transactional(readOnly = true)
    public PesosResponse pesos(UUID establecimientoId, LocalDate desde, LocalDate hasta, UUID loteId,
                               Categoria categoria) {
        exigirEstablecimiento(establecimientoId);
        LocalDate fin = hasta != null ? hasta : LocalDate.now(reloj);
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO);
        validarPeriodo(inicio, fin);

        // Ya vienen ordenados por animal y fecha
        Map<UUID, List<Evento>> porAnimal = new LinkedHashMap<>();
        for (Evento e : repository.pesajes(establecimientoId, TipoEvento.PESAJE, inicio, fin)) {
            Animal a = e.getAnimal();
            if (loteId != null && (a.getLote() == null || !loteId.equals(a.getLote().getId()))) continue;
            if (categoria != null && a.getCategoria() != categoria) continue;
            porAnimal.computeIfAbsent(a.getId(), k -> new ArrayList<>()).add(e);
        }

        List<PesosResponse.PorAnimal> animales = new ArrayList<>();
        Map<LocalDate, List<BigDecimal>> pesosPorFecha = new TreeMap<>();
        for (List<Evento> pesajes : porAnimal.values()) {
            animales.add(resumenDelAnimal(pesajes));
            for (Evento e : pesajes) {
                pesosPorFecha.computeIfAbsent(e.getFecha(), f -> new ArrayList<>()).add(kg(e));
            }
        }
        animales.sort(Comparator.comparing(PesosResponse.PorAnimal::caravana));

        List<PesosResponse.PorFecha> porFecha = pesosPorFecha.entrySet().stream()
            .map(f -> new PesosResponse.PorFecha(f.getKey(), f.getValue().size(), promedio(f.getValue())))
            .toList();

        Map<Categoria, List<PesosResponse.PorAnimal>> porCat = new EnumMap<>(Categoria.class);
        animales.forEach(a -> porCat.computeIfAbsent(a.categoria(), c -> new ArrayList<>()).add(a));
        List<PesosResponse.PorCategoria> porCategoria = porCat.entrySet().stream()
            .map(c -> new PesosResponse.PorCategoria(c.getKey(), c.getValue().size(),
                promedio(c.getValue().stream().map(PesosResponse.PorAnimal::pesoFinal).toList()),
                promedio(c.getValue().stream().map(PesosResponse.PorAnimal::gananciaDiaria).toList())))
            .toList();

        List<BigDecimal> ganancias = animales.stream()
            .map(PesosResponse.PorAnimal::gananciaDiaria).filter(Objects::nonNull).toList();
        var resumen = new PesosResponse.Resumen(
            animales.size(),
            porAnimal.values().stream().mapToInt(List::size).sum(),
            ganancias.size(),
            promedio(animales.stream().map(PesosResponse.PorAnimal::pesoInicial).toList()),
            promedio(animales.stream().map(PesosResponse.PorAnimal::pesoFinal).toList()),
            promedio(ganancias));

        return new PesosResponse(inicio, fin, resumen, porFecha, porCategoria, animales);
    }

    private PesosResponse.PorAnimal resumenDelAnimal(List<Evento> pesajes) {
        Evento primero = pesajes.get(0);
        Evento ultimo = pesajes.get(pesajes.size() - 1);
        Animal a = primero.getAnimal();
        BigDecimal pesoInicial = kg(primero);
        BigDecimal pesoFinal = kg(ultimo);
        int dias = (int) ChronoUnit.DAYS.between(primero.getFecha(), ultimo.getFecha());

        Integer diasPeriodo = null;
        BigDecimal variacion = null;
        BigDecimal ganancia = null;
        // Con una sola pesada (o todas el mismo día) no hay ganancia
        if (dias > 0) {
            diasPeriodo = dias;
            variacion = pesoFinal.subtract(pesoInicial).setScale(2, RoundingMode.HALF_UP);
            ganancia = variacion.divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP);
        }
        return new PesosResponse.PorAnimal(a.getId(), a.getCaravana(), a.getCategoria(),
            a.getLote() == null ? null : a.getLote().getNombre(), pesajes.size(), primero.getFecha(), pesoInicial,
            ultimo.getFecha(), pesoFinal, diasPeriodo, variacion, ganancia);
    }

    // ---------- Existencias para SENASA ----------

    @Transactional(readOnly = true)
    public ExistenciasResponse existencias(UUID establecimientoId) {
        Establecimiento e = exigirEstablecimiento(establecimientoId);
        List<ExistenciasResponse.Animal> animales = repository.animales(establecimientoId, EstadoAnimal.ACTIVO)
            .stream()
            .map(a -> new ExistenciasResponse.Animal(a.getId(), a.getCaravana(), a.getCaravanaElectronica(),
                a.getSexo(), a.getCategoria(), a.getLote() == null ? null : a.getLote().getNombre(),
                a.getRodeo() == null ? null : a.getRodeo().getNombre()))
            // Por lote (los sin lote al final) y por caravana: el orden en que se recorre la manga
            .sorted(Comparator.comparing(ExistenciasResponse.Animal::lote,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(ExistenciasResponse.Animal::caravana))
            .toList();
        return new ExistenciasResponse(
            new ExistenciasResponse.Establecimiento(e.getId(), e.getNombre(), e.getRenspa(), e.getLocalidad(),
                e.getProvincia()),
            reloj.instant(),
            stockService.calcular(establecimientoId),
            animales);
    }

    // ---------- Comunes ----------

    private Establecimiento exigirEstablecimiento(UUID establecimientoId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        return establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));
    }

    private static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("desde", "No puede ser posterior a hasta")));
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMO) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("desde", "El período no puede superar los 3 años")));
        }
    }

    private BigDecimal kg(Evento e) {
        try {
            JsonNode datos = json.readTree(e.getDatos());
            return datos.get("kg").decimalValue();
        } catch (JsonProcessingException | NullPointerException ex) {
            throw new IllegalStateException("Pesaje sin kg: " + e.getId(), ex);
        }
    }

    /** Promedio con 2 decimales, sin contar los null. null si no hay ninguno. */
    private static BigDecimal promedio(List<BigDecimal> valores) {
        List<BigDecimal> conValor = valores.stream().filter(Objects::nonNull).toList();
        if (conValor.isEmpty()) return null;
        return conValor.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(conValor.size()), 2, RoundingMode.HALF_UP);
    }
}
