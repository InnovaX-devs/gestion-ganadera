package com.innovax.ganadera.common.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Entidad que solo existe en tests para verificar el aislamiento multi-tenant. */
@Getter
@Entity
@Table(name = "registro_prueba")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroPrueba extends TenantEntity {

    @Column(nullable = false)
    private String nombre;

    public RegistroPrueba(String nombre) {
        this.nombre = nombre;
    }
}
