package com.innovax.ganadera.animal.categoria;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.animal.categoria.ReglasCategoria.Transicion;
import com.innovax.ganadera.animal.categoria.dto.CambioCategoriaResponse;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;

import lombok.RequiredArgsConstructor;

/**
 * Recalcula la categoría de los animales (issue #16) y deja cada cambio en el historial.
 *
 * <p>Quién lo llama:
 * <ul>
 *   <li>Los módulos de eventos, al registrar un destete, un parto o un pesaje:
 *       {@code categoriaService.aplicar(animal, new Hecho.Parto(fecha))}.</li>
 *   <li>El job diario ({@link CambioCategoriaJob}), para los cambios por edad.</li>
 *   <li>{@code AnimalService}, cuando el usuario cambia la categoría a mano.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    /** Las categorías que pueden cambiar por edad. Las demás (vaca, vaquillona, novillo, toro) no. */
    static final Set<Categoria> CAMBIAN_POR_EDAD =
        EnumSet.of(Categoria.TERNERA, Categoria.TERNERO, Categoria.NOVILLITO, Categoria.TORITO);

    /** Ninguna cadena de categorías es más larga que esto (ternero → torito → toro); es solo un tope de seguridad. */
    private static final int MAXIMO_DE_PASOS = 4;

    private final ReglasCategoria reglas;
    private final CambioCategoriaRepository historial;
    private final AnimalRepository animalRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final AutorizacionService autorizacion;
    private final Clock reloj;

    /**
     * Aplica las reglas al animal por lo que le pasó y guarda cada cambio. Si quedó atrasado más de
     * una categoría, avanza hasta donde corresponda (un paso por fila del historial).
     *
     * <p>No autoriza: corre dentro de la transacción del evento que lo llama, que ya lo hizo. El
     * animal tiene que estar cargado en esa transacción para que el cambio de categoría se guarde.
     *
     * @return los cambios hechos, en orden; vacía si no cambió nada o el animal no está activo
     */
    @Transactional
    public List<CambioCategoria> aplicar(Animal animal, Hecho hecho) {
        if (!animal.isActivo()) return List.of();
        List<CambioCategoria> cambios = new ArrayList<>();
        for (int paso = 0; paso < MAXIMO_DE_PASOS; paso++) {
            Optional<Transicion> transicion = reglas.siguiente(
                animal.getCategoria(), animal.isCastrado(), animal.getFechaNacimiento(), hecho);
            if (transicion.isEmpty()) break;
            cambios.add(cambiar(animal, transicion.get().categoriaNueva(), transicion.get().motivo(),
                hecho.fecha(), null));
        }
        return cambios;
    }

    /**
     * Revisa por edad todos los animales activos de la organización actual. Lo usa el job, que fija
     * la organización con {@link TenantContext#comoOrganizacion}.
     *
     * @return cuántos animales cambiaron de categoría
     */
    @Transactional
    public int recalcularPorEdad(LocalDate hoy) {
        LocalDate nacidosHasta = hoy.minusMonths(reglas.mesesMinimosParaCambiarPorEdad());
        Hecho hecho = new Hecho.PasoDelTiempo(hoy);
        int cambiados = 0;
        for (Animal animal : animalRepository.candidatosACambioPorEdad(
                EstadoAnimal.ACTIVO, CAMBIAN_POR_EDAD, nacidosHasta)) {
            if (!aplicar(animal, hecho).isEmpty()) cambiados++;
        }
        return cambiados;
    }

    /**
     * Deja en el historial un cambio que hizo el usuario editando el animal. Si la categoría no
     * cambió, no hace nada.
     */
    @Transactional
    public void registrarCambioManual(Animal animal, Categoria anterior) {
        if (anterior == animal.getCategoria()) return;
        UUID usuarioId = TenantContext.requerirUsuario().userId();
        historial.save(new CambioCategoria(animal, anterior, animal.getCategoria(), MotivoCambio.MANUAL,
            LocalDate.now(reloj), usuarioId));
    }

    /** Historial de un animal, el cambio más reciente primero. Lo puede ver cualquier miembro. */
    @Transactional(readOnly = true)
    public List<CambioCategoriaResponse> historial(UUID establecimientoId, UUID animalId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));
        animalRepository.findByIdAndEstablecimientoId(animalId, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Animal", animalId));
        return historial.findByAnimalIdOrderByFechaDescCreatedAtDesc(animalId).stream()
            .map(CambioCategoriaResponse::from)
            .toList();
    }

    private CambioCategoria cambiar(Animal animal, Categoria nueva, MotivoCambio motivo, LocalDate fecha,
                                    UUID usuarioId) {
        Categoria anterior = animal.getCategoria();
        animal.setCategoria(nueva);
        // Del ternero se hereda lo indicado; en novillito/novillo queda true y en torito/toro, false.
        animal.setCastrado(nueva.castrado(animal.isCastrado()));
        return historial.save(new CambioCategoria(animal, anterior, nueva, motivo, fecha, usuarioId));
    }
}
