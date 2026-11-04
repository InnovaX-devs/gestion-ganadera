package com.innovax.ganadera.evento.tipos;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.agrupacion.Agrupacion;
import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.Rodeo;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;
import com.innovax.ganadera.evento.tipos.MovimientoDatos.Ref;
import com.innovax.ganadera.evento.tipos.MovimientoDatos.Ubicacion;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;

import lombok.RequiredArgsConstructor;

/**
 * Issue #22. Cambia al animal de lote, rodeo o establecimiento y deja el recorrido en el evento
 * (ver {@link MovimientoDatos}). La ubicación actual del animal es la del último movimiento; el
 * historial son sus eventos.
 *
 * <ul>
 *   <li>Se pueden mover varios animales a la vez, o todo un lote o rodeo (como en los demás eventos).</li>
 *   <li>El destino tiene que ser un establecimiento de la misma organización (otra no se ve: ver
 *       {@code @TenantId}) donde el usuario también pueda gestionar animales.</li>
 *   <li>Todo o nada: si a un animal no se lo puede mover (ya está en el destino, la caravana ya
 *       existe allá), no se mueve ninguno.</li>
 * </ul>
 *
 * Al anularlo el animal vuelve a donde estaba, siempre que siga donde lo dejó este movimiento: si
 * después se movió de nuevo, hay que anular primero ese último movimiento.
 */
@Component
@RequiredArgsConstructor
public class MovimientoHandler implements TipoEventoHandler<MovimientoDatos> {

    private final EstablecimientoRepository establecimientoRepository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;
    private final AnimalRepository animalRepository;
    private final AutorizacionService autorizacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.MOVIMIENTO;
    }

    @Override
    public Class<MovimientoDatos> claseDatos() {
        return MovimientoDatos.class;
    }

    // ---------- Validación ----------

    @Override
    public void validar(MovimientoDatos datos, Contexto contexto) {
        if (datos.quitarLote() && datos.loteDestinoId() != null) {
            throw new BusinessException("Elegí un lote o quitalo, no las dos cosas");
        }
        if (datos.quitarRodeo() && datos.rodeoDestinoId() != null) {
            throw new BusinessException("Elegí un rodeo o quitalo, no las dos cosas");
        }
        boolean hayCambio = cambiaDeEstablecimiento(datos, contexto.establecimiento())
            || datos.loteDestinoId() != null || datos.rodeoDestinoId() != null
            || datos.quitarLote() || datos.quitarRodeo();
        if (!hayCambio) {
            throw new BusinessException("Indicá a dónde se mueven los animales");
        }
    }

    /**
     * Resuelve el destino contra la base (una vez por pedido): que el establecimiento, el lote y el
     * rodeo existan y sean del destino, y que el usuario pueda gestionar animales ahí.
     */
    @Override
    public MovimientoDatos completar(MovimientoDatos datos, Contexto contexto) {
        Establecimiento origen = contexto.establecimiento();
        boolean otroEstablecimiento = cambiaDeEstablecimiento(datos, origen);
        Establecimiento destino = otroEstablecimiento ? establecimientoDeDestino(datos.establecimientoDestinoId()) : origen;
        if (otroEstablecimiento) {
            validarCaravanasLibres(destino, contexto.animales());
        }

        String donde = otroEstablecimiento ? "el establecimiento de destino" : "este establecimiento";
        Lote lote = datos.loteDestinoId() == null ? null
            : loteRepository.findByIdAndEstablecimientoIdAndActivoTrue(datos.loteDestinoId(), destino.getId())
                .orElseThrow(() -> new BusinessException("El lote elegido no existe en " + donde));
        Rodeo rodeo = datos.rodeoDestinoId() == null ? null
            : rodeoRepository.findByIdAndEstablecimientoIdAndActivoTrue(datos.rodeoDestinoId(), destino.getId())
                .orElseThrow(() -> new BusinessException("El rodeo elegido no existe en " + donde));

        return datos.conDestino(new Ubicacion(ref(destino), refDe(lote), refDe(rodeo)));
    }

    /** Anota de dónde sale este animal y en qué queda, y corta si el movimiento no le cambia nada. */
    @Override
    public MovimientoDatos completarPorAnimal(MovimientoDatos datos, Animal animal, Contexto contexto) {
        Ubicacion desde = ubicacionDe(animal);
        Ubicacion pedido = datos.hacia();
        boolean otroEstablecimiento = !Objects.equals(id(pedido.establecimiento()), id(desde.establecimiento()));

        // En otro establecimiento el lote y el rodeo de origen no valen: queda en los pedidos o sin asignar
        Ref lote = otroEstablecimiento || datos.quitarLote() || pedido.lote() != null ? pedido.lote() : desde.lote();
        Ref rodeo = otroEstablecimiento || datos.quitarRodeo() || pedido.rodeo() != null ? pedido.rodeo() : desde.rodeo();
        Ubicacion hacia = new Ubicacion(pedido.establecimiento(), lote, rodeo);

        if (mismaUbicacion(desde, hacia)) {
            throw new BusinessException(animal.getCaravana() + " ya está en el destino elegido");
        }
        return datos.conRecorrido(desde, hacia);
    }

    // ---------- Efectos ----------

    @Override
    public void aplicar(Evento evento, MovimientoDatos datos) {
        Animal animal = evento.getAnimal();
        Ubicacion hacia = datos.hacia();
        if (!Objects.equals(id(hacia.establecimiento()), animal.getEstablecimiento().getId())) {
            animal.trasladarA(establecimientoRepository.getReferenceById(id(hacia.establecimiento())));
        }
        animal.setLote(hacia.lote() == null ? null : loteRepository.getReferenceById(hacia.lote().id()));
        animal.setRodeo(hacia.rodeo() == null ? null : rodeoRepository.getReferenceById(hacia.rodeo().id()));
    }

    /**
     * Devuelve al animal a donde estaba. Solo si sigue donde lo dejó este movimiento y todo lo de
     * origen sigue existiendo; si no, no se anula nada y se dice qué hay que hacer.
     */
    @Override
    public void revertir(Evento evento, MovimientoDatos datos) {
        Animal animal = evento.getAnimal();
        Ubicacion desde = datos.desde();
        Ubicacion hacia = datos.hacia();
        String caravana = animal.getCaravana();

        if (!animal.isActivo()) {
            throw new BusinessException("No se puede anular el movimiento: " + caravana
                + " ya no está en el stock (se vendió o se dio de baja)");
        }
        if (!mismaUbicacion(ubicacionDe(animal), hacia)) {
            throw new BusinessException("No se puede anular el movimiento: " + caravana
                + " se movió después. Anulá primero su último movimiento.");
        }

        UUID origenId = id(desde.establecimiento());
        if (!Objects.equals(origenId, id(hacia.establecimiento()))) {
            // Sacar animales de un campo también es gestionarlos ahí
            autorizacion.exigir(id(hacia.establecimiento()), Permiso.GESTIONAR_ANIMALES);
            Establecimiento origen = establecimientoRepository.findByIdAndActivoTrue(origenId)
                .orElseThrow(() -> new BusinessException("No se puede anular el movimiento: el establecimiento "
                    + desde.establecimiento().nombre() + " ya no existe"));
            if (animalRepository.existsByEstablecimientoIdAndCaravanaAndEstado(origenId, caravana, EstadoAnimal.ACTIVO)) {
                throw new ConflictException("No se puede anular el movimiento: ya hay otro animal con la caravana "
                    + caravana + " en " + origen.getNombre());
            }
            animal.trasladarA(origen);
        }

        animal.setLote(desde.lote() == null ? null
            : loteRepository.findByIdAndEstablecimientoIdAndActivoTrue(desde.lote().id(), origenId)
                .orElseThrow(() -> new BusinessException("No se puede anular el movimiento: el lote "
                    + desde.lote().nombre() + " ya no existe")));
        animal.setRodeo(desde.rodeo() == null ? null
            : rodeoRepository.findByIdAndEstablecimientoIdAndActivoTrue(desde.rodeo().id(), origenId)
                .orElseThrow(() -> new BusinessException("No se puede anular el movimiento: el rodeo "
                    + desde.rodeo().nombre() + " ya no existe")));
    }

    // ---------- Auxiliares ----------

    private static boolean cambiaDeEstablecimiento(MovimientoDatos datos, Establecimiento origen) {
        return datos.establecimientoDestinoId() != null && !datos.establecimientoDestinoId().equals(origen.getId());
    }

    /**
     * Un establecimiento de otra organización no se encuentra (el filtro por organización lo oculta).
     * Si es de la organización pero el usuario no es miembro o no gestiona animales ahí, corta
     * {@link AutorizacionService}: 404 o 403, como en cualquier otra operación sobre ese campo.
     */
    private Establecimiento establecimientoDeDestino(UUID id) {
        Establecimiento destino = establecimientoRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> new BusinessException("El establecimiento de destino no existe"));
        autorizacion.exigir(destino.getId(), Permiso.GESTIONAR_ANIMALES);
        return destino;
    }

    /** La caravana es única entre los animales activos de cada establecimiento. */
    private void validarCaravanasLibres(Establecimiento destino, List<Animal> animales) {
        List<String> caravanas = animales.stream().map(Animal::getCaravana).toList();
        animalRepository.findByEstablecimientoIdAndCaravanaInAndEstado(destino.getId(), caravanas, EstadoAnimal.ACTIVO)
            .stream().findFirst()
            .ifPresent(repetido -> {
                throw new ConflictException("Ya hay un animal con la caravana " + repetido.getCaravana()
                    + " en " + destino.getNombre());
            });
    }

    private static Ubicacion ubicacionDe(Animal animal) {
        return new Ubicacion(ref(animal.getEstablecimiento()), refDe(animal.getLote()), refDe(animal.getRodeo()));
    }

    /** Compara por id: los nombres pueden haber cambiado. */
    private static boolean mismaUbicacion(Ubicacion a, Ubicacion b) {
        return Objects.equals(id(a.establecimiento()), id(b.establecimiento()))
            && Objects.equals(id(a.lote()), id(b.lote()))
            && Objects.equals(id(a.rodeo()), id(b.rodeo()));
    }

    private static UUID id(Ref ref) {
        return ref == null ? null : ref.id();
    }

    private static Ref ref(Establecimiento establecimiento) {
        return new Ref(establecimiento.getId(), establecimiento.getNombre());
    }

    private static Ref refDe(Agrupacion agrupacion) {
        return agrupacion == null ? null : new Ref(agrupacion.getId(), agrupacion.getNombre());
    }
}
