package com.innovax.ganadera.adjunto;

import java.util.UUID;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.common.tenant.TenantEntity;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Foto o archivo adjunto a un animal ({@link #animal}) o a una operación de eventos
 * ({@link #operacionId}); nunca a los dos. El contenido está en el {@link AlmacenArchivos} bajo
 * {@link #clave}; acá solo el registro. No se modifica: si está mal, se elimina y se sube de nuevo.
 */
@Getter
@Entity
@Table(name = "adjunto")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adjunto extends TenantEntity {

    /** Donde se subió. En un adjunto de animal, el establecimiento puede no ser el actual del animal. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @Column(name = "operacion_id", updatable = false)
    private UUID operacionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id", updatable = false)
    private Animal animal;

    /** Lo genera quien sube: reintentar la misma carga no la duplica. */
    @Column(name = "carga_id", nullable = false, updatable = false)
    private UUID cargaId;

    @Column(nullable = false, length = 150, updatable = false)
    private String nombre;

    /** Lo detecta el servidor mirando el contenido; no es lo que diga el cliente. */
    @Column(name = "tipo_contenido", nullable = false, length = 50, updatable = false)
    private String tipoContenido;

    @Column(nullable = false, updatable = false)
    private long tamanio;

    @Column(nullable = false, length = 100, updatable = false)
    private String clave;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    public Adjunto(Establecimiento establecimiento, UUID operacionId, Animal animal, UUID cargaId, String nombre,
                   String tipoContenido, long tamanio, String clave, Usuario usuario) {
        this.establecimiento = establecimiento;
        this.operacionId = operacionId;
        this.animal = animal;
        this.cargaId = cargaId;
        this.nombre = nombre;
        this.tipoContenido = tipoContenido;
        this.tamanio = tamanio;
        this.clave = clave;
        this.usuario = usuario;
    }
}
