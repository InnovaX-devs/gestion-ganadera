package com.innovax.ganadera.evento.tipos;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.animal.categoria.CategoriaService;
import com.innovax.ganadera.animal.categoria.Hecho;
import com.innovax.ganadera.common.error.ApiError;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.DatosInvalidosException;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.TipoEventoHandler;

import lombok.RequiredArgsConstructor;

/**
 * Issue #18. El evento va sobre la madre (una sola por evento) y:
 *
 * <ul>
 *   <li>crea cada cría viva como animal: ternera o ternero, nacido en la fecha del evento, hijo de
 *       la madre y en su mismo lote y rodeo (la cría queda al pie de la madre);</li>
 *   <li>registra el parto en la madre: si era vaquillona pasa a vaca (lo resuelve
 *       {@link CategoriaService}, que deja el cambio en el historial de categorías);</li>
 *   <li>los nacidos muertos no se crean como animales, pero cuentan como parto.</li>
 * </ul>
 *
 * Al anularlo se borran las crías que creó (si todavía no tienen historia propia) y, si la madre no
 * tiene otro parto vigente, vuelve a la categoría que tenía.
 */
@Component
@RequiredArgsConstructor
public class NacimientoHandler implements TipoEventoHandler<NacimientoDatos> {

    /**
     * La gestación dura unos 283 días: dos partos de la misma madre más cerca que esto son un error
     * de carga (o mellizos cargados por separado, que van juntos en un mismo nacimiento).
     */
    static final int DIAS_MINIMOS_ENTRE_PARTOS = 300;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AnimalRepository animalRepository;
    private final EventoRepository eventoRepository;
    private final CategoriaService categoriaService;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.NACIMIENTO;
    }

    @Override
    public Class<NacimientoDatos> claseDatos() {
        return NacimientoDatos.class;
    }

    // ---------- Validación ----------

    @Override
    public void validar(NacimientoDatos datos, Contexto contexto) {
        if (contexto.animales().size() != 1) {
            throw new BusinessException("Elegí una sola madre: los nacimientos se registran de a un parto");
        }
        Animal madre = contexto.animales().getFirst();
        if (madre.getSexo() != Sexo.HEMBRA) {
            throw new BusinessException("La madre tiene que ser hembra");
        }
        validarPartoCercano(madre, contexto.fecha());
        validarCaravanas(datos, contexto.establecimiento().getId());
    }

    private void validarPartoCercano(Animal madre, LocalDate fecha) {
        eventoRepository.findByAnimalIdAndTipoAndDeletedAtIsNullAndFechaBetween(madre.getId(), TipoEvento.NACIMIENTO,
                fecha.minusDays(DIAS_MINIMOS_ENTRE_PARTOS), fecha.plusDays(DIAS_MINIMOS_ENTRE_PARTOS))
            .stream().findFirst()
            .ifPresent(otro -> {
                throw new BusinessException(madre.getCaravana() + " ya tiene un parto registrado el "
                    + FECHA.format(otro.getFecha()) + ". Si fueron mellizos, anulá ese nacimiento y cargá "
                    + "las crías juntas en uno solo.");
            });
    }

    /**
     * Cada cría viva necesita caravana, distinta de las otras crías del parto y de los animales
     * activos del establecimiento. Los errores de formulario salen con el campo de la cría
     * ({@code datos.crias[1].caravana}).
     */
    private void validarCaravanas(NacimientoDatos datos, UUID establecimientoId) {
        List<ApiError.FieldError> errores = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < datos.crias().size(); i++) {
            NacimientoDatos.Cria cria = datos.crias().get(i);
            if (cria.nacidoMuerto()) continue;
            String campo = "datos.crias[" + i + "].caravana";
            if (cria.caravana() == null) {
                errores.add(new ApiError.FieldError(campo, "Ingresá la caravana de la cría"));
            } else if (!vistas.add(cria.caravana())) {
                errores.add(new ApiError.FieldError(campo, "Está repetida en otra cría"));
            }
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        for (String caravana : vistas) {
            if (animalRepository.existsByEstablecimientoIdAndCaravanaAndEstado(
                    establecimientoId, caravana, EstadoAnimal.ACTIVO)) {
                throw new ConflictException("Ya hay un animal con la caravana " + caravana + " en este establecimiento");
            }
        }
    }

    // ---------- Efectos ----------

    @Override
    public void aplicar(Evento evento, NacimientoDatos datos) {
        Animal madre = evento.getAnimal();
        for (NacimientoDatos.Cria cria : datos.vivas()) {
            Categoria categoria = cria.sexo() == Sexo.HEMBRA ? Categoria.TERNERA : Categoria.TERNERO;
            Animal ternero = new Animal(evento.getEstablecimiento(), cria.caravana(), cria.sexo(), categoria);
            ternero.setFechaNacimiento(evento.getFecha());
            ternero.setMadre(madre);
            ternero.setLote(madre.getLote());
            ternero.setRodeo(madre.getRodeo());
            ternero.setEventoNacimientoId(evento.getId());
            animalRepository.save(ternero);
        }
        categoriaService.aplicar(madre, new Hecho.Parto(evento.getFecha()));
    }

    /**
     * Las crías solo se borran si siguen como las dejó el nacimiento: activas, sin eventos y sin
     * crías propias. Si alguna ya tiene historia, no se anula nada y se avisa qué hay que hacer.
     */
    @Override
    public void revertir(Evento evento, NacimientoDatos datos) {
        List<Animal> crias = animalRepository.findByEventoNacimientoId(evento.getId());
        for (Animal cria : crias) {
            String motivo = motivoParaNoBorrar(cria);
            if (motivo != null) {
                throw new BusinessException("No se puede anular el nacimiento: la cría " + cria.getCaravana()
                    + " " + motivo);
            }
        }
        for (Animal cria : crias) {
            eventoRepository.borrarAnuladosDelAnimal(cria.getId());
            animalRepository.delete(cria);
        }

        Animal madre = evento.getAnimal();
        boolean tieneOtroParto = eventoRepository.existsByAnimalIdAndTipoAndDeletedAtIsNullAndIdNot(
            madre.getId(), TipoEvento.NACIMIENTO, evento.getId());
        if (!tieneOtroParto) {
            categoriaService.revertirParto(madre, evento.getFecha());
        }
    }

    private String motivoParaNoBorrar(Animal cria) {
        if (!cria.isActivo()) {
            return "ya no está en el stock (se vendió o se dio de baja)";
        }
        if (eventoRepository.existsByAnimalIdAndDeletedAtIsNull(cria.getId())) {
            return "tiene eventos registrados. Anulalos primero.";
        }
        if (animalRepository.existsByMadreIdOrPadreId(cria.getId(), cria.getId())) {
            return "figura como madre o padre de otros animales";
        }
        return null;
    }
}
