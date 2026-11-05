package com.innovax.ganadera.adjunto;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.innovax.ganadera.adjunto.dto.AdjuntoResponse;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.EventoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fotos y archivos adjuntos a eventos y animales (#43). Quién puede qué:
 *
 * <ul>
 *   <li><b>Ver</b>: cualquier miembro del establecimiento.</li>
 *   <li><b>Subir o eliminar en un evento</b>: quien puede registrar ese tipo de evento (el mismo permiso
 *       que para anularlo). Un adjunto de un evento anulado deja de verse con él.</li>
 *   <li><b>Subir o eliminar en un animal</b>: quien puede gestionar animales.</li>
 * </ul>
 *
 * <p>Orden de las operaciones: el archivo se guarda primero y la fila después; si la transacción
 * se revierte el archivo se borra, y al eliminar el archivo se borra recién cuando la fila ya se
 * eliminó de verdad. Lo peor que puede pasar es un archivo suelto en el almacén, nunca una fila
 * apuntando a un archivo que no existe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjuntoService {

    private static final long MB = 1024 * 1024;

    private final AdjuntoRepository repository;
    private final EventoRepository eventoRepository;
    private final AnimalRepository animalRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacion;
    private final AlmacenArchivos almacen;
    private final AdjuntosProperties propiedades;

    /** Lo necesario para devolver el archivo: el contenido lo cierra quien lo lee. */
    public record Descarga(String nombre, String tipoContenido, long tamanio, InputStream contenido) {
    }

    // ---------- Subir ----------

    /** Adjunta el archivo a la operación: se ve en todos los eventos que la componen. */
    @Transactional
    public AdjuntoResponse subirAEvento(UUID establecimientoId, UUID operacionId, MultipartFile archivo,
                                        UUID cargaId) {
        Rol rol = autorizacion.rolEn(establecimientoId);
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);
        Evento evento = primerEventoVigente(establecimientoId, operacionId);
        exigirPermisoDelTipo(rol, evento);
        return guardar(establecimiento, operacionId, null, archivo, cargaId);
    }

    @Transactional
    public AdjuntoResponse subirAAnimal(UUID establecimientoId, UUID animalId, MultipartFile archivo, UUID cargaId) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_ANIMALES);
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);
        Animal animal = animal(establecimientoId, animalId);
        return guardar(establecimiento, null, animal, archivo, cargaId);
    }

    private AdjuntoResponse guardar(Establecimiento establecimiento, UUID operacionId, Animal animal,
                                    MultipartFile archivo, UUID cargaId) {
        UUID carga = cargaId != null ? cargaId : UUID.randomUUID();

        // Reintento de una carga que ya llegó (ej: la app offline no recibió la respuesta)
        Optional<Adjunto> repetido = repository.findByCargaId(carga);
        if (repetido.isPresent()) {
            if (!esDe(repetido.get(), establecimiento, operacionId, animal)) {
                throw new BusinessException("Ese identificador de carga ya está en uso");
            }
            return AdjuntoResponse.de(repetido.get());
        }

        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Elegí un archivo para adjuntar");
        }
        long maximo = propiedades.tamanioMaximo().toBytes();
        if (archivo.getSize() > maximo) {
            throw new BusinessException("El archivo supera el máximo de " + Math.max(1, maximo / MB) + " MB");
        }
        long cantidad = operacionId != null
            ? repository.countByOperacionId(operacionId)
            : repository.countByAnimalId(animal.getId());
        if (cantidad >= propiedades.maximoPorElemento()) {
            throw new BusinessException("Se pueden adjuntar hasta " + propiedades.maximoPorElemento() + " archivos");
        }
        String tipo = tipoDe(archivo);
        if (tipo == null) {
            throw new BusinessException("Solo se pueden adjuntar fotos (JPG, PNG, WebP o HEIC) y archivos PDF");
        }

        var usuarioActual = TenantContext.requerirUsuario();
        String clave = usuarioActual.organizacionId() + "/" + UUID.randomUUID();
        try (InputStream contenido = archivo.getInputStream()) {
            almacen.guardar(clave, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
        borrarArchivoSiSeRevierte(clave);

        Adjunto adjunto = new Adjunto(establecimiento, operacionId, animal, carga,
            nombreSeguro(archivo.getOriginalFilename(), tipo), tipo, archivo.getSize(), clave,
            usuarioRepository.getReferenceById(usuarioActual.userId()));
        return AdjuntoResponse.de(repository.saveAndFlush(adjunto));
    }

    private static boolean esDe(Adjunto a, Establecimiento establecimiento, UUID operacionId, Animal animal) {
        return a.getEstablecimiento().getId().equals(establecimiento.getId())
            && Objects.equals(a.getOperacionId(), operacionId)
            && Objects.equals(a.getAnimal() == null ? null : a.getAnimal().getId(),
                animal == null ? null : animal.getId());
    }

    private static String tipoDe(MultipartFile archivo) {
        try (InputStream in = archivo.getInputStream()) {
            return FirmaArchivo.detectar(in.readNBytes(FirmaArchivo.BYTES_NECESARIOS));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }

    /**
     * Solo el nombre, sin carpetas ni caracteres de control, para mostrarlo. El archivo se guarda con
     * una clave propia: este nombre no se usa nunca para armar una ruta.
     */
    static String nombreSeguro(String original, String tipoContenido) {
        String nombre = original == null ? "" : original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (nombre.isEmpty()) {
            nombre = "archivo" + switch (tipoContenido) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/heic" -> ".heic";
                default -> ".pdf";
            };
        }
        // Si es larguísimo se conserva el final, que es donde está la extensión
        return nombre.length() > 150 ? nombre.substring(nombre.length() - 150) : nombre;
    }

    // ---------- Consultar ----------

    /** Los de un animal, del más nuevo al más viejo. Siguen al animal si cambia de establecimiento. */
    @Transactional(readOnly = true)
    public List<AdjuntoResponse> deAnimal(UUID establecimientoId, UUID animalId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        establecimientoActivo(establecimientoId);
        animal(establecimientoId, animalId);
        return repository.findByAnimalIdOrderByCreatedAtDesc(animalId).stream().map(AdjuntoResponse::de).toList();
    }

    /**
     * Los de varias operaciones juntas, por operación (una sola consulta para toda una página de
     * eventos). No controla permisos: lo hace quien ya autorizó la lectura de esos eventos.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<AdjuntoResponse>> deOperaciones(Collection<UUID> operacionIds) {
        if (operacionIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByOperacionIdInOrderByCreatedAtAsc(operacionIds).stream()
            .collect(Collectors.groupingBy(Adjunto::getOperacionId,
                Collectors.mapping(AdjuntoResponse::de, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public Descarga descargar(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        Adjunto adjunto = visible(establecimientoId, id);
        try {
            return new Descarga(adjunto.getNombre(), adjunto.getTipoContenido(), adjunto.getTamanio(),
                almacen.abrir(adjunto.getClave()));
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof NoSuchFileException) {
                log.error("Falta en el almacén el archivo del adjunto {} ({})", id, adjunto.getClave());
                throw new NotFoundException("El archivo ya no está disponible");
            }
            throw e;
        }
    }

    // ---------- Eliminar ----------

    @Transactional
    public void eliminar(UUID establecimientoId, UUID id) {
        Rol rol = autorizacion.rolEn(establecimientoId);
        Adjunto adjunto = visible(establecimientoId, id);
        if (adjunto.getAnimal() != null) {
            if (!rol.puede(Permiso.GESTIONAR_ANIMALES)) {
                throw new AccessDeniedException("El rol " + rol + " no puede eliminar archivos de animales");
            }
        } else {
            exigirPermisoDelTipo(rol, primerEventoVigente(establecimientoId, adjunto.getOperacionId()));
        }
        repository.delete(adjunto);
        borrarArchivoTrasElCommit(adjunto.getClave());
    }

    /** Al borrar un animal cargado por error se borran también sus archivos. */
    @Transactional
    public void borrarDeAnimal(UUID animalId) {
        List<Adjunto> adjuntos = repository.findByAnimalId(animalId);
        repository.deleteAll(adjuntos);
        adjuntos.forEach(a -> borrarArchivoTrasElCommit(a.getClave()));
    }

    /**
     * Al borrarse los últimos eventos de una operación (animal cargado por error), sus archivos
     * quedarían sin dueño: se borran. Las operaciones que todavía tienen eventos no se tocan.
     */
    @Transactional
    public void borrarDeOperacionesSinEventos(Collection<UUID> operacionIds) {
        for (UUID operacionId : operacionIds) {
            if (eventoRepository.existsByOperacionId(operacionId)) {
                continue;
            }
            List<Adjunto> adjuntos = repository.findByOperacionId(operacionId);
            repository.deleteAll(adjuntos);
            adjuntos.forEach(a -> borrarArchivoTrasElCommit(a.getClave()));
        }
    }

    // ---------- Reglas comunes ----------

    /**
     * El adjunto, si se puede ver desde ese establecimiento. Los de animal, si el animal está ahí;
     * los de operación, si hay un evento vigente de esa operación registrado ahí. Todo lo demás es
     * 404 (también un adjunto de otra organización, que el filtro multi-tenant ni siquiera encuentra).
     */
    private Adjunto visible(UUID establecimientoId, UUID id) {
        establecimientoActivo(establecimientoId);
        Adjunto adjunto = repository.findById(id).orElseThrow(() -> NotFoundException.of("Archivo", id));
        boolean visible = adjunto.getAnimal() != null
            ? animalRepository.existsByIdAndEstablecimientoId(adjunto.getAnimal().getId(), establecimientoId)
            : eventoRepository.existsByOperacionIdAndEstablecimientoIdAndDeletedAtIsNull(
                adjunto.getOperacionId(), establecimientoId);
        if (!visible) {
            throw NotFoundException.of("Archivo", id);
        }
        return adjunto;
    }

    private Evento primerEventoVigente(UUID establecimientoId, UUID operacionId) {
        return eventoRepository.findByOperacionIdOrderByCreatedAtAsc(operacionId).stream()
            .filter(e -> e.getEstablecimiento().getId().equals(establecimientoId) && !e.isAnulado())
            .findFirst()
            .orElseThrow(() -> NotFoundException.of("Evento", operacionId));
    }

    private static void exigirPermisoDelTipo(Rol rol, Evento evento) {
        if (!evento.getTipo().permitidoPara(rol)) {
            throw new AccessDeniedException("El rol " + rol + " no puede modificar archivos de " + evento.getTipo());
        }
    }

    private Animal animal(UUID establecimientoId, UUID animalId) {
        return animalRepository.findByIdAndEstablecimientoId(animalId, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Animal", animalId));
    }

    private Establecimiento establecimientoActivo(UUID id) {
        return establecimientoRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    // ---------- Archivo y transacción ----------

    /** Si la transacción no termina bien, el archivo recién guardado no tiene fila: se borra. */
    private void borrarArchivoSiSeRevierte(String clave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    borrarArchivo(clave);
                }
            }
        });
    }

    /** El archivo se borra recién cuando la fila ya se eliminó de verdad. */
    private void borrarArchivoTrasElCommit(String clave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            borrarArchivo(clave);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                borrarArchivo(clave);
            }
        });
    }

    private void borrarArchivo(String clave) {
        try {
            almacen.borrar(clave);
        } catch (RuntimeException e) {
            // No se puede deshacer nada a esta altura: queda un archivo suelto, que no molesta a nadie
            log.warn("No se pudo borrar del almacén el archivo {}", clave, e);
        }
    }
}
