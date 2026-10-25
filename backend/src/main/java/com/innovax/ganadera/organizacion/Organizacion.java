package com.innovax.ganadera.organizacion;

import com.innovax.ganadera.common.tenant.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una cuenta del sistema (productor o empresa). Es el "tenant": todos sus datos cuelgan de acá. */
@Getter
@Setter
@Entity
@Table(name = "organizacion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organizacion extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nombre;

    public Organizacion(String nombre) {
        this.nombre = nombre;
    }
}
