package com.innovax.ganadera.establecimiento;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.dto.EstablecimientoRequest;
import com.innovax.ganadera.establecimiento.dto.EstablecimientoResponse;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Membresia;
import com.innovax.ganadera.membresia.MembresiaRepository;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * ABM de establecimientos. No hay que filtrar por organización: lo hace Hibernate (@TenantId).
 * Un establecimiento de otra organización simplemente "no existe" (404).
 *
 * <p>Qué puede hacer cada rol está en {@link Rol} y en docs/permisos.md. Cada operación empieza
 * pidiendo el permiso a {@link AutorizacionService}; quien crea un establecimiento queda como dueño.
 */
@Service
@RequiredArgsConstructor
public class EstablecimientoService {

    private final EstablecimientoRepository repository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;
    private final AnimalRepository animalRepository;
    private final MembresiaRepository membresiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacion;

    @Transactional(readOnly = true)
    public List<EstablecimientoResponse> listar() {
        // Solo los establecimientos donde el usuario tiene una membresía
        UUID usuarioId = TenantContext.requerirUsuario().userId();
        return membresiaRepository.findDelUsuario(usuarioId).stream()
            .map(m -> EstablecimientoResponse.from(m.getEstablecimiento(), m.getRol()))
            .toList();
    }

    @Transactional(readOnly = true)
    public EstablecimientoResponse obtener(UUID id) {
        Rol rol = autorizacion.exigir(id, Permiso.VER);
        return EstablecimientoResponse.from(buscar(id), rol);
    }

    @Transactional
    public EstablecimientoResponse crear(EstablecimientoRequest request) {
        if (repository.existsByNombreIgnoreCaseAndActivoTrue(request.nombre())) {
            throw nombreRepetido(request.nombre());
        }
        Establecimiento establecimiento = new Establecimiento(request.nombre());
        copiarDatos(request, establecimiento);
        repository.save(establecimiento);
        // Quien lo crea queda como dueño
        UUID usuarioId = TenantContext.requerirUsuario().userId();
        membresiaRepository.save(
            new Membresia(usuarioRepository.getReferenceById(usuarioId), establecimiento, Rol.DUENO));
        return EstablecimientoResponse.from(establecimiento, Rol.DUENO);
    }

    @Transactional
    public EstablecimientoResponse actualizar(UUID id, EstablecimientoRequest request) {
        Rol rol = autorizacion.exigir(id, Permiso.EDITAR_ESTABLECIMIENTO);
        Establecimiento establecimiento = buscar(id);
        if (repository.existsByNombreIgnoreCaseAndActivoTrueAndIdNot(request.nombre(), id)) {
            throw nombreRepetido(request.nombre());
        }
        copiarDatos(request, establecimiento);
        return EstablecimientoResponse.from(repository.saveAndFlush(establecimiento), rol);
    }

    /**
     * Baja lógica. Solo si no tiene animales, lotes ni rodeos activos, para que no quede hacienda
     * "perdida" en un campo eliminado por error.
     */
    @Transactional
    public void eliminar(UUID id) {
        autorizacion.exigir(id, Permiso.ELIMINAR_ESTABLECIMIENTO);
        Establecimiento establecimiento = buscar(id);
        long animales = animalRepository.countByEstablecimientoIdAndEstado(id, EstadoAnimal.ACTIVO);
        long lotes = loteRepository.countByEstablecimientoIdAndActivoTrue(id);
        long rodeos = rodeoRepository.countByEstablecimientoIdAndActivoTrue(id);
        if (animales + lotes + rodeos > 0) {
            List<String> partes = java.util.stream.Stream.of(
                    cantidad(animales, "animal", "animales"),
                    cantidad(lotes, "lote", "lotes"),
                    cantidad(rodeos, "rodeo", "rodeos"))
                .filter(t -> !t.isEmpty())
                .toList();
            // "1 lote y 1 rodeo" / "3 animales, 1 lote y 1 rodeo"
            String detalle = partes.size() == 1 ? partes.get(0)
                : String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
            throw new BusinessException("No se puede eliminar: todavía tiene " + detalle + ". Eliminalos primero.");
        }
        establecimiento.darDeBaja();
    }

    private static String cantidad(long n, String singular, String plural) {
        return n == 0 ? "" : n + " " + (n == 1 ? singular : plural);
    }

    private Establecimiento buscar(UUID id) {
        return repository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    private static void copiarDatos(EstablecimientoRequest request, Establecimiento e) {
        e.setNombre(request.nombre());
        e.setLocalidad(request.localidad());
        e.setProvincia(request.provincia());
        e.setSuperficieHa(request.superficieHa());
        e.setRenspa(request.renspa());
        e.setLatitud(request.latitud());
        e.setLongitud(request.longitud());
    }

    private static ConflictException nombreRepetido(String nombre) {
        return new ConflictException("Ya tenés un establecimiento llamado \"" + nombre + "\"");
    }
}
