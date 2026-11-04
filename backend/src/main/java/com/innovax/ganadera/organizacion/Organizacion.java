package com.innovax.ganadera.organizacion;

import java.time.LocalDate;

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

    /** Plan contratado (#35). Ver {@code PlanService} para el que rige hoy. */
    @Column(name = "plan_codigo", nullable = false, length = 20)
    private String planCodigo = "INICIAL";

    /** Último día de la prueba gratis, o null si no tiene prueba */
    @Column(name = "prueba_hasta")
    private LocalDate pruebaHasta;

    /** Pagó por fuera de Mercado Pago hasta este día inclusive (lo carga el panel de Innovax, #35) */
    @Column(name = "pago_manual_hasta")
    private LocalDate pagoManualHasta;

    /** Suspendida desde el panel de administración: nadie de la cuenta puede entrar (#35) */
    @Column(nullable = false)
    private boolean suspendida;

    public Organizacion(String nombre) {
        this.nombre = nombre;
    }
}
