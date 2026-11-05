package com.innovax.ganadera.adjunto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class AlmacenEnDiscoTest {

    @TempDir
    Path directorio;

    private AlmacenEnDisco almacen() {
        return new AlmacenEnDisco(new AdjuntosProperties("disco", directorio.toString(), DataSize.ofMegabytes(1), 3, null));
    }

    @Test
    void guardaLeeYBorra() throws IOException {
        var almacen = almacen();
        byte[] contenido = {1, 2, 3, 4};

        almacen.guardar("org-1/archivo", new ByteArrayInputStream(contenido));
        assertTrue(Files.exists(directorio.resolve("org-1/archivo")));
        try (var in = almacen.abrir("org-1/archivo")) {
            assertArrayEquals(contenido, in.readAllBytes());
        }
        // No quedan temporales a medio escribir
        try (var archivos = Files.list(directorio.resolve("org-1"))) {
            assertTrue(archivos.allMatch(p -> p.getFileName().toString().equals("archivo")));
        }

        almacen.borrar("org-1/archivo");
        assertFalse(Files.exists(directorio.resolve("org-1/archivo")));
        almacen.borrar("org-1/archivo"); // borrar algo que no está no es un error
    }

    @Test
    void abrirUnoQueNoExisteLanzaUncheckedIOException() {
        assertThrows(UncheckedIOException.class, () -> almacen().abrir("org-1/no-esta"));
    }

    @Test
    void unaClaveNoPuedeSalirDelDirectorio() {
        var almacen = almacen();
        var vacio = new ByteArrayInputStream(new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> almacen.guardar("../fuera", vacio));
        assertThrows(IllegalArgumentException.class, () -> almacen.abrir("org-1/../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> almacen.borrar("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> almacen.borrar(""));
    }
}
