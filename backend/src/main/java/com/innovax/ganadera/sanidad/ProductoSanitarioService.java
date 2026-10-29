package com.innovax.ganadera.sanidad;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.sanidad.dto.ProductoSanitarioRequest;
import com.innovax.ganadera.sanidad.dto.ProductoSanitarioResponse;

import lombok.RequiredArgsConstructor;

/**
 * Catálogo de productos sanitarios de un establecimiento. Verlo: cualquier miembro. Agregar,
 * editar y dar de baja productos: quien pueda registrar sanidad ({@link Permiso#REGISTRAR_SANIDAD}),
 * que es también quien carga los tratamientos.
 */
@Service
@RequiredArgsConstructor
public class ProductoSanitarioService {

    private final ProductoSanitarioRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final AutorizacionService autorizacion;

    @Transactional(readOnly = true)
    public List<ProductoSanitarioResponse> listar(UUID establecimientoId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoActivo(establecimientoId);
        return repository.findByEstablecimientoIdAndActivoTrueOrderByNombreAsc(establecimientoId).stream()
            .map(ProductoSanitarioResponse::from)
            .toList();
    }

    @Transactional
    public ProductoSanitarioResponse crear(UUID establecimientoId, ProductoSanitarioRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);
        if (repository.existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrue(establecimientoId, request.nombre())) {
            throw nombreRepetido(request.nombre());
        }
        var producto = new ProductoSanitario(establecimiento, request.nombre(), request.tipo());
        copiar(request, producto);
        return ProductoSanitarioResponse.from(repository.save(producto));
    }

    /**
     * Cambiar un producto no toca los tratamientos ya registrados: cada uno guarda el nombre, el
     * tipo y la carencia que tenía al aplicarse.
     */
    @Transactional
    public ProductoSanitarioResponse actualizar(UUID establecimientoId, UUID id, ProductoSanitarioRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        ProductoSanitario producto = buscar(establecimientoId, id);
        if (repository.existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(
                establecimientoId, request.nombre(), id)) {
            throw nombreRepetido(request.nombre());
        }
        producto.setNombre(request.nombre());
        producto.setTipo(request.tipo());
        copiar(request, producto);
        return ProductoSanitarioResponse.from(repository.saveAndFlush(producto));
    }

    /** Baja lógica: deja de ofrecerse al cargar tratamientos, pero los ya registrados lo siguen nombrando. */
    @Transactional
    public void eliminar(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.REGISTRAR_SANIDAD);
        buscar(establecimientoId, id).darDeBaja();
    }

    private void copiar(ProductoSanitarioRequest request, ProductoSanitario producto) {
        producto.setDiasCarencia(request.diasCarenciaOCero());
        producto.setDosisHabitual(request.dosisHabitual());
        producto.setViaHabitual(request.viaHabitual());
    }

    private ProductoSanitario buscar(UUID establecimientoId, UUID id) {
        establecimientoActivo(establecimientoId);
        return repository.findByIdAndEstablecimientoIdAndActivoTrue(id, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Producto", id));
    }

    private Establecimiento establecimientoActivo(UUID id) {
        return establecimientoRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    private ConflictException nombreRepetido(String nombre) {
        return new ConflictException("Ya hay un producto llamado \"" + nombre + "\" en este establecimiento");
    }
}
