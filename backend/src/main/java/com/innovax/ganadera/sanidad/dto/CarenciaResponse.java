package com.innovax.ganadera.sanidad.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Carencia vigente de un animal: no se debe faenar ni vender para consumo antes de {@code libreDesde}
 * (el más lejano de sus tratamientos). Un animal sin carencia no tiene este objeto.
 */
public record CarenciaResponse(LocalDate libreDesde, List<Tratamiento> tratamientos) {

    /** Un tratamiento que todavía tiene carencia. {@code libreDesde} = fecha + días de carencia. */
    public record Tratamiento(String producto, LocalDate fecha, int diasCarencia, LocalDate libreDesde) {
    }
}
