package com.innovax.ganadera.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.agrupacion.Agrupacion;
import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.Rodeo;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.common.tenant.BaseEntity;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.sanidad.ProductoSanitario;
import com.innovax.ganadera.sync.Cursor.Posicion;
import com.innovax.ganadera.sync.Cursor.Tabla;
import com.innovax.ganadera.sync.dto.SyncResponse;
import com.innovax.ganadera.usuario.Usuario;

import jakarta.persistence.EntityManager;

/**
 * Pull de la sincronización (#30): lo que cambió desde el cursor, tabla por tabla, ordenado por
 * {@code (updated_at, id)}. Usa JPQL para que Hibernate filtre por organización (@TenantId).
 */
@Service
class SyncPullService {

    /** Cada tabla: entidad JPQL, cómo se filtra por establecimiento y qué se trae junto. */
    private record Consulta(String entidad, String establecimiento, String joins) {
    }

    private static final Map<Tabla, Consulta> CONSULTAS = new EnumMap<>(Map.of(
        Tabla.LOTE, new Consulta("Lote", "e.establecimiento.id", ""),
        Tabla.RODEO, new Consulta("Rodeo", "e.establecimiento.id", ""),
        Tabla.PRODUCTO, new Consulta("ProductoSanitario", "e.establecimiento.id", ""),
        Tabla.ANIMAL, new Consulta("Animal", "e.establecimiento.id", ""),
        Tabla.EVENTO, new Consulta("Evento", "e.establecimiento.id",
            "join fetch e.usuario left join fetch e.animal left join fetch e.lote"),
        Tabla.BAJA, new Consulta("SyncBaja", "e.establecimientoId", "")));

    private final EntityManager em;
    private final ObjectMapper json;
    private final SyncProperties propiedades;
    private final Clock reloj;

    SyncPullService(EntityManager em, ObjectMapper json, SyncProperties propiedades, Clock reloj) {
        this.em = em;
        this.json = json;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    record Resultado(Cursor cursor, boolean hayMas, SyncResponse.Cambios cambios, List<SyncResponse.Baja> bajas) {
    }

    @Transactional(readOnly = true)
    public Resultado traer(Cursor desde, int limite) {
        UUID est = desde.establecimientoId();
        // Una vuelta nueva fija su tope; las páginas siguientes de la misma vuelta lo mantienen
        Instant corte = desde.corte() != null ? desde.corte() : reloj.instant().minus(propiedades.margenValido());
        Map<Tabla, Posicion> posiciones = new EnumMap<>(Tabla.class);
        posiciones.putAll(desde.posiciones());

        Map<Tabla, List<?>> filas = new EnumMap<>(Tabla.class);
        int restante = limite;
        boolean hayMas = false;
        for (Tabla tabla : Tabla.values()) {
            if (restante == 0) {
                hayMas = true;
                break;
            }
            // Se pide una de más para saber si queda algo en esta tabla
            List<? extends BaseEntity> pagina = consultar(tabla, est, posiciones.getOrDefault(tabla, Posicion.INICIO),
                corte, restante + 1);
            boolean quedan = pagina.size() > restante;
            if (quedan) pagina = pagina.subList(0, restante);
            if (!pagina.isEmpty()) {
                BaseEntity ultima = pagina.get(pagina.size() - 1);
                posiciones.put(tabla, new Posicion(ultima.getUpdatedAt(), ultima.getId()));
            }
            filas.put(tabla, pagina);
            restante -= pagina.size();
            if (quedan) {
                hayMas = true;
                break;
            }
        }

        var cambios = new SyncResponse.Cambios(
            mapear(filas, Tabla.LOTE, (Lote l) -> agrupacion(l)),
            mapear(filas, Tabla.RODEO, (Rodeo r) -> agrupacion(r)),
            mapear(filas, Tabla.PRODUCTO, SyncPullService::producto),
            mapear(filas, Tabla.ANIMAL, SyncPullService::animal),
            mapear(filas, Tabla.EVENTO, this::evento));
        List<SyncResponse.Baja> bajas = bajas(est, mapear(filas, Tabla.BAJA, (SyncBaja b) -> b));

        // Terminada la vuelta, la próxima arranca con un tope nuevo
        Cursor siguiente = new Cursor(est, hayMas ? corte : null, posiciones);
        return new Resultado(siguiente, hayMas, cambios, bajas);
    }

    private List<? extends BaseEntity> consultar(Tabla tabla, UUID est, Posicion desde, Instant corte, int maximo) {
        Consulta c = CONSULTAS.get(tabla);
        String jpql = "select e from " + c.entidad() + " e " + c.joins()
            + " where " + c.establecimiento() + " = :est and e.updatedAt <= :corte"
            + " and (e.updatedAt > :ts or (e.updatedAt = :ts and e.id > :id))"
            + " order by e.updatedAt, e.id";
        return em.createQuery(jpql, BaseEntity.class)
            .setParameter("est", est)
            .setParameter("corte", corte)
            .setParameter("ts", desde.updatedAt())
            .setParameter("id", desde.id())
            .setMaxResults(maximo)
            .getResultList();
    }

    @SuppressWarnings("unchecked")
    private static <E, R> List<R> mapear(Map<Tabla, List<?>> filas, Tabla tabla, Function<E, R> mapeo) {
        List<?> lista = filas.getOrDefault(tabla, List.of());
        List<R> resultado = new ArrayList<>(lista.size());
        for (Object fila : lista) resultado.add(mapeo.apply((E) fila));
        return resultado;
    }

    /**
     * Las bajas de animales que hoy vuelven a estar en este establecimiento (salieron y se anuló el
     * movimiento) no se mandan: su fila ya viaja en {@code cambios.animales}.
     */
    private List<SyncResponse.Baja> bajas(UUID est, List<SyncBaja> bajas) {
        if (bajas.isEmpty()) return List.of();
        Set<UUID> ids = new HashSet<>(bajas.stream().map(SyncBaja::getEntidadId).toList());
        Set<UUID> presentes = new HashSet<>(em.createQuery(
                "select a.id from Animal a where a.id in :ids and a.establecimiento.id = :est", UUID.class)
            .setParameter("ids", ids)
            .setParameter("est", est)
            .getResultList());
        return bajas.stream()
            .filter(b -> !presentes.contains(b.getEntidadId()))
            .map(b -> new SyncResponse.Baja(b.getEntidad(), b.getEntidadId(), b.getUpdatedAt()))
            .toList();
    }

    private static SyncResponse.Agrupacion agrupacion(Agrupacion a) {
        return new SyncResponse.Agrupacion(a.getId(), a.getNombre(), a.getDescripcion(), a.isActivo(), a.getUpdatedAt());
    }

    private static SyncResponse.Producto producto(ProductoSanitario p) {
        return new SyncResponse.Producto(p.getId(), p.getNombre(), p.getTipo(), p.getDiasCarencia(),
            p.getDosisHabitual(), p.getViaHabitual(), p.isActivo(), p.getUpdatedAt());
    }

    private static SyncResponse.Animal animal(Animal a) {
        // getId() de una relación lazy no la carga: no hay consultas extra
        return new SyncResponse.Animal(a.getId(), a.getCaravana(), a.getCaravanaElectronica(), a.getSexo(),
            a.getCategoria(), a.getRaza(), a.getFechaNacimiento(), a.isFechaNacimientoEstimada(), a.isCastrado(),
            idDe(a.getLote()), idDe(a.getRodeo()), idDe(a.getMadre()), idDe(a.getPadre()), a.getEstado(),
            a.getObservaciones(), a.getUpdatedAt());
    }

    private SyncResponse.Evento evento(Evento e) {
        Usuario u = e.getUsuario();
        String usuario = u.getApellido() == null ? u.getNombre() : u.getNombre() + " " + u.getApellido();
        return new SyncResponse.Evento(e.getId(), e.getOperacionId(), e.getTipo(), e.getFecha(), idDe(e.getAnimal()),
            idDe(e.getLote()), leer(e.getDatos()), usuario, e.getCreatedAt(), e.isAnulado(), e.getUpdatedAt());
    }

    private static UUID idDe(BaseEntity entidad) {
        return entidad == null ? null : entidad.getId();
    }

    private JsonNode leer(String datos) {
        try {
            return json.readTree(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Datos de evento ilegibles", e);
        }
    }
}
