package com.innovax.ganadera.plan;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un plan con sus límites (#35). Están en la tabla {@code plan} para poder cambiarlos sin tocar
 * código. Un límite null es "sin límite". No es de una organización: lo comparten todas.
 */
@Getter
@Entity
@Table(name = "plan")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan {

    @Id
    @Column(length = 20)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String nombre;

    /** De menor a mayor: sirve para comparar planes */
    @Column(nullable = false)
    private int orden;

    @Column(name = "max_animales")
    private Integer maxAnimales;

    @Column(name = "max_establecimientos")
    private Integer maxEstablecimientos;

    @Column(name = "max_usuarios")
    private Integer maxUsuarios;

    /**
     * Precio por mes en pesos, para la suscripción con Mercado Pago. Null o 0: no se puede contratar
     * online. Se cambia desde el panel de administración.
     */
    @Setter
    @Column(name = "precio_mensual")
    private BigDecimal precioMensual;
}
