package com.innovax.ganadera.evento.tipos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;
import com.innovax.ganadera.sanidad.CarenciaService;
import com.innovax.ganadera.sanidad.dto.CarenciaResponse;

import lombok.RequiredArgsConstructor;

/**
 * Venta (#23). El animal queda VENDIDO y sale del stock.
 *
 * <ul>
 *   <li><b>Venta masiva:</b> varios animales (o un lote / rodeo) con el mismo comprador y precio. El
 *       peso se da como total del grupo y cada animal queda con el promedio; para pesos distintos se
 *       usa la carga por animal ({@code EventoService.registrarPorAnimal}).</li>
 *   <li><b>Importe:</b> lo calcula el servidor por animal: precio × kg si es por kg, o el precio si
 *       es por cabeza.</li>
 *   <li><b>Carencia:</b> un animal con carencia sanitaria vigente no se vende, salvo que quien carga
 *       lo confirme explícitamente ({@code confirmarCarencia}); queda constancia en el evento.</li>
 * </ul>
 *
 * Al anularla el animal vuelve al stock, con su lote y rodeo de antes (la venta no los toca).
 */
@Component
@RequiredArgsConstructor
public class VentaHandler implements TipoEventoHandler<VentaDatos> {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CarenciaService carenciaService;
    private final AnimalRepository animalRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VENTA;
    }

    @Override
    public Class<VentaDatos> claseDatos() {
        return VentaDatos.class;
    }

    @Override
    public void validar(VentaDatos datos, Contexto contexto) {
        if (datos.kg() != null && datos.pesoTotal() != null) {
            throw new BusinessException("Indicá el peso de cada animal o el peso total, no los dos");
        }
        if (datos.kg() != null && contexto.animales().size() > 1) {
            throw new BusinessException(
                "Con varios animales indicá el peso total (se reparte en partes iguales), o cargá el peso de cada uno");
        }
        if (datos.modalidad() == ModalidadPrecio.POR_KG && datos.kg() == null && datos.pesoTotal() == null) {
            throw new BusinessException("Para vender por kg indicá el peso");
        }
        if (datos.pesoTotal() != null && promedio(datos.pesoTotal(), contexto.animales().size()).signum() <= 0) {
            throw new BusinessException("El peso total es demasiado bajo para la cantidad de animales");
        }
    }

    /** Frena los animales en carencia, deja el peso por animal y calcula el importe. */
    @Override
    public VentaDatos completar(VentaDatos datos, Contexto contexto) {
        boolean enCarencia = controlarCarencia(datos, contexto);

        int cantidad = contexto.animales().size();
        BigDecimal kg = datos.pesoTotal() != null ? promedio(datos.pesoTotal(), cantidad) : datos.kg();
        BigDecimal importe = datos.modalidad() == ModalidadPrecio.POR_KG
            ? datos.precio().multiply(kg).setScale(2, RoundingMode.HALF_UP)
            : datos.precio().setScale(2, RoundingMode.HALF_UP);

        return new VentaDatos(datos.comprador(), datos.modalidad(), datos.precio(), kg,
            datos.pesoTotal(), datos.pesoTotal() != null ? cantidad : null,
            datos.documento(), enCarencia ? Boolean.TRUE : null, importe);
    }

    @Override
    public void aplicar(Evento evento, VentaDatos datos) {
        SalidaDelStock.sacar(evento.getAnimal(), EstadoAnimal.VENDIDO);
    }

    @Override
    public void revertir(Evento evento, VentaDatos datos) {
        SalidaDelStock.devolver(evento.getAnimal(), EstadoAnimal.VENDIDO, "la venta", animalRepository);
    }

    /** @return true si había animales en carencia y se vendieron igual por confirmación expresa. */
    private boolean controlarCarencia(VentaDatos datos, Contexto contexto) {
        Map<UUID, CarenciaResponse> carencias = carenciaService.deAnimales(
            contexto.establecimiento().getId(), contexto.animales().stream().map(Animal::getId).toList());
        if (carencias.isEmpty()) return false;
        if (datos.confirmaCarencia()) return true;

        String detalle = contexto.animales().stream()
            .filter(a -> carencias.containsKey(a.getId()))
            .map(a -> a.getCaravana() + " (hasta el " + carencias.get(a.getId()).libreDesde().format(FECHA) + ")")
            .limit(5)
            .collect(Collectors.joining(", "));
        int total = carencias.size();
        throw new BusinessException((total == 1 ? "Hay 1 animal en carencia sanitaria: " : "Hay " + total
            + " animales en carencia sanitaria: ") + detalle + (total > 5 ? " y otros" : "")
            + ". Confirmá la venta para venderlos igual");
    }

    private static BigDecimal promedio(BigDecimal total, int cantidad) {
        return total.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
    }
}
