package com.innovax.ganadera.adjunto;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Guarda cada archivo como un archivo común dentro de {@code app.adjuntos.directorio}. */
@Component
@ConditionalOnProperty(name = "app.adjuntos.almacen", havingValue = "disco", matchIfMissing = true)
public class AlmacenEnDisco implements AlmacenArchivos {

    private final Path raiz;

    public AlmacenEnDisco(AdjuntosProperties propiedades) {
        if (propiedades.directorio() == null || propiedades.directorio().isBlank()) {
            throw new IllegalStateException(
                "Falta ADJUNTOS_DIR: con ADJUNTOS_ALMACEN=disco hay que indicar la carpeta de los archivos");
        }
        this.raiz = Path.of(propiedades.directorio()).toAbsolutePath().normalize();
    }

    @Override
    public void guardar(String clave, InputStream contenido) {
        Path destino = resolver(clave);
        Path temporal = null;
        try {
            Files.createDirectories(destino.getParent());
            // Se escribe aparte y se renombra al final, así nadie lee un archivo a medio escribir
            temporal = Files.createTempFile(destino.getParent(), "subiendo-", ".tmp");
            Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + clave, e);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignorado) {
                    // Es un temporal; si no se puede borrar no cambia el resultado
                }
            }
        }
    }

    @Override
    public InputStream abrir(String clave) {
        try {
            return Files.newInputStream(resolver(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el archivo " + clave, e);
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            Files.deleteIfExists(resolver(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el archivo " + clave, e);
        }
    }

    /** La clave nunca puede apuntar fuera del directorio del almacén. */
    private Path resolver(String clave) {
        Path destino = raiz.resolve(clave).normalize();
        if (!destino.startsWith(raiz) || destino.equals(raiz)) {
            throw new IllegalArgumentException("Clave de archivo inválida: " + clave);
        }
        return destino;
    }
}
