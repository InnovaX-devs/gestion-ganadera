package com.innovax.ganadera.adjunto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code app.adjuntos.*} en application.yml.
 *
 * @param almacen           dónde se guardan los archivos: {@code disco} (default, desarrollo y tests) o
 *                          {@code r2} (Cloudflare R2 o cualquier almacén compatible con S3)
 * @param directorio        dónde guarda los archivos el {@link AlmacenEnDisco}. Si se usa en producción tiene que
 *                          ser un volumen persistente: si el disco se borra al redesplegar, se pierden las fotos.
 * @param tamanioMaximo     tope por archivo. También es el límite de Spring para el multipart.
 * @param maximoPorElemento cuántos archivos puede tener un animal o una operación de eventos
 * @param r2                credenciales y bucket, solo para {@code almacen=r2}
 */
@ConfigurationProperties(prefix = "app.adjuntos")
public record AdjuntosProperties(
    String almacen,
    String directorio,
    DataSize tamanioMaximo,
    int maximoPorElemento,
    R2 r2
) {

    /**
     * @param bucket    nombre del bucket
     * @param endpoint  {@code https://<ACCOUNT_ID>.r2.cloudflarestorage.com}
     * @param accessKey Access Key ID del token de API
     * @param secretKey Secret Access Key del token de API
     */
    public record R2(String bucket, String endpoint, String accessKey, String secretKey) {
    }
}
