package com.innovax.ganadera.membresia;

import com.innovax.ganadera.common.tenant.TenantEntity;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** El rol que tiene un usuario en un establecimiento. Un usuario puede tener uno por cada establecimiento. */
@Getter
@Entity
@Table(name = "membresia")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membresia extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @Setter
    @Convert(converter = RolConverter.class)
    @Column(nullable = false, length = 20)
    private Rol rol;

    public Membresia(Usuario usuario, Establecimiento establecimiento, Rol rol) {
        this.usuario = usuario;
        this.establecimiento = establecimiento;
        this.rol = rol;
    }
}
