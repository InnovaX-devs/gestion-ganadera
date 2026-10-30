package com.innovax.ganadera.mercado;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.mercado.dto.CotizacionResponse;
import com.innovax.ganadera.mercado.dto.HistoricoResponse;
import com.innovax.ganadera.mercado.dto.MercadoHoyResponse;
import com.innovax.ganadera.mercado.dto.RelacionReposicionResponse;
import com.innovax.ganadera.mercado.fuente.Lectura;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Cotizaciones de mercado (#27): guarda la foto diaria y la consulta. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CotizacionService {

    /** Histórico por defecto: los últimos 30 días. */
    static final int DIAS_POR_DEFECTO = 30;
    /** Rango máximo del histórico, para no devolver series enormes. */
    static final int DIAS_MAXIMO = 366;

    private final CotizacionRepository repository;
    private final Clock reloj;

    /**
     * Guarda la foto de {@code fecha} para los productos de una fuente. Lo que no se pudo leer
     * ({@code error} de toda la fuente, o un {@link Lectura#fallo} puntual) mantiene el último valor
     * conocido, con estado ANTERIOR y el error guardado.
     *
     * <p>Se puede correr varias veces el mismo día: actualiza la foto de ese día. Un reintento que
     * falla no pisa un valor que ya se había leído bien ese día.
     */
    @Transactional
    public void registrar(LocalDate fecha, String fuente, Set<Producto> productos, List<Lectura> lecturas,
                          String errorFuente) {
        Map<Producto, Lectura> porProducto = lecturas.stream()
            .filter(l -> productos.contains(l.producto()))
            .collect(Collectors.toMap(Lectura::producto, Function.identity(), (a, b) -> a));

        for (Producto producto : productos) {
            Lectura lectura = porProducto.get(producto);
            Optional<Cotizacion> existente = repository.findByFechaAndProducto(fecha, producto);
            Cotizacion cotizacion = existente.orElseGet(() -> new Cotizacion(fecha, producto));

            if (lectura != null && lectura.esOk()) {
                cotizacion.actual(lectura.valor(), lectura.fechaDato(), fuente, lectura.nota());
            } else {
                String error = errorFuente != null ? errorFuente
                    : lectura != null && lectura.error() != null ? lectura.error()
                    : "La fuente no informó este producto";
                if (existente.isPresent() && existente.get().getEstado() == EstadoCotizacion.ACTUAL) {
                    log.warn("Cotizaciones {} {}: falló un reintento, se mantiene lo leído hoy ({})",
                        fecha, producto, error);
                    continue;
                }
                repository.findFirstByProductoAndFechaBeforeAndValorIsNotNullOrderByFechaDesc(producto, fecha)
                    .ifPresentOrElse(
                        ultima -> cotizacion.anterior(ultima, fuente, error),
                        () -> cotizacion.sinDato(fuente, error));
                log.warn("Cotizaciones {} {}: {} falló, queda {} ({})",
                    fecha, producto, fuente, cotizacion.getEstado(), error);
            }
            repository.save(cotizacion);
        }
    }

    @Transactional(readOnly = true)
    public boolean hayCotizaciones() {
        return repository.count() > 0;
    }

    @Transactional(readOnly = true)
    public MercadoHoyResponse hoy() {
        Map<Producto, Cotizacion> ultimas = new EnumMap<>(Producto.class);
        repository.buscarUltimas().forEach(c -> ultimas.put(c.getProducto(), c));
        // La foto anterior de cada producto, para la variación (una consulta por producto: son 9)
        Map<Producto, Cotizacion> anteriores = new EnumMap<>(Producto.class);
        ultimas.forEach((producto, ultima) -> repository
            .findFirstByProductoAndFechaBeforeAndValorIsNotNullOrderByFechaDesc(producto, ultima.getFecha())
            .ifPresent(anterior -> anteriores.put(producto, anterior)));

        List<CotizacionResponse> cotizaciones = Arrays.stream(Producto.values())
            .map(p -> ultimas.containsKey(p)
                ? CotizacionResponse.from(ultimas.get(p), anteriores.get(p))
                : CotizacionResponse.sinDato(p))
            .toList();
        LocalDate fecha = ultimas.values().stream().map(Cotizacion::getFecha).max(LocalDate::compareTo).orElse(null);
        Instant actualizadoEn = ultimas.values().stream()
            .map(Cotizacion::getUpdatedAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);

        return new MercadoHoyResponse(fecha, actualizadoEn, cotizaciones,
            relacion(ultimas.get(Producto.TERNERO), ultimas.get(Producto.NOVILLO),
                anteriores.get(Producto.TERNERO), anteriores.get(Producto.NOVILLO)));
    }

    /**
     * @param productos vacío o null: todos
     * @param desde por defecto, 30 días antes de {@code hasta}
     * @param hasta por defecto, hoy
     */
    @Transactional(readOnly = true)
    public HistoricoResponse historico(Collection<Producto> productos, LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now(reloj);
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO);
        validarRango(inicio, fin);

        Set<Producto> pedidos = productos == null || productos.isEmpty()
            ? EnumSet.allOf(Producto.class) : EnumSet.copyOf(productos);
        // Ternero y novillo se buscan siempre: hacen falta para la relación de reposición
        Set<Producto> aBuscar = EnumSet.copyOf(pedidos);
        aBuscar.add(Producto.TERNERO);
        aBuscar.add(Producto.NOVILLO);

        List<Cotizacion> filas = repository.findByFechaBetweenAndProductoInOrderByFechaAsc(inicio, fin, aBuscar);

        Map<Producto, List<HistoricoResponse.Punto>> series = new EnumMap<>(Producto.class);
        pedidos.forEach(p -> series.put(p, new ArrayList<>()));
        Map<LocalDate, Map<Producto, Cotizacion>> porDia = new TreeMap<>();
        for (Cotizacion c : filas) {
            if (series.containsKey(c.getProducto())) {
                series.get(c.getProducto()).add(
                    new HistoricoResponse.Punto(c.getFecha(), c.getValor(), c.getFechaDato(), c.getEstado()));
            }
            porDia.computeIfAbsent(c.getFecha(), f -> new EnumMap<>(Producto.class)).put(c.getProducto(), c);
        }

        List<HistoricoResponse.PuntoRelacion> relacion = porDia.entrySet().stream()
            .map(e -> {
                Cotizacion ternero = e.getValue().get(Producto.TERNERO);
                Cotizacion novillo = e.getValue().get(Producto.NOVILLO);
                var valor = RelacionReposicion.calcular(
                    ternero == null ? null : ternero.getValor(), novillo == null ? null : novillo.getValor());
                return valor == null ? null : new HistoricoResponse.PuntoRelacion(e.getKey(), valor);
            })
            .filter(Objects::nonNull)
            .toList();

        return new HistoricoResponse(inicio, fin, series, relacion);
    }

    private static RelacionReposicionResponse relacion(Cotizacion ternero, Cotizacion novillo,
                                                       Cotizacion terneroAnterior, Cotizacion novilloAnterior) {
        if (ternero == null || novillo == null) return null;
        var valor = RelacionReposicion.calcular(ternero.getValor(), novillo.getValor());
        if (valor == null) return null;
        boolean actualizada = ternero.getEstado() == EstadoCotizacion.ACTUAL
            && novillo.getEstado() == EstadoCotizacion.ACTUAL;
        var anterior = terneroAnterior == null || novilloAnterior == null ? null
            : RelacionReposicion.calcular(terneroAnterior.getValor(), novilloAnterior.getValor());
        return new RelacionReposicionResponse(valor, ternero.getValor(), novillo.getValor(),
            ternero.getFechaDato(), novillo.getFechaDato(), actualizada, anterior);
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("desde", "No puede ser posterior a hasta")));
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMO) {
            throw new DatosInvalidosException(List.of(
                new ApiError.FieldError("desde", "El rango no puede superar los " + DIAS_MAXIMO + " días")));
        }
    }
}
