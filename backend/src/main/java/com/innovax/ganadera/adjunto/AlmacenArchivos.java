package com.innovax.ganadera.adjunto;

import java.io.InputStream;

/**
 * Dónde viven los bytes de los adjuntos. Hoy hay una sola implementación, {@link AlmacenEnDisco}; si
 * se pasa a S3 o R2 (ver docs/decisiones/0005-adjuntos.md) se escribe otra y no cambia nada más.
 *
 * <p>La {@code clave} la arma el servidor ({@code <organizacion>/<uuid>}), nunca sale del cliente.
 * Todos los métodos lanzan {@link java.io.UncheckedIOException} si falla el almacén.
 */
public interface AlmacenArchivos {

    /** Guarda el contenido completo o nada: un archivo a medias no puede quedar visible. */
    void guardar(String clave, InputStream contenido);

    /** Quien llama cierra el stream. */
    InputStream abrir(String clave);

    /** Si no existe, no hace nada. */
    void borrar(String clave);
}
