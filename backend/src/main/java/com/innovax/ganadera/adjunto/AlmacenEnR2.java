package com.innovax.ganadera.adjunto;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Guarda los archivos en Cloudflare R2 (o cualquier almacén compatible con S3), ver
 * docs/decisiones/0005-adjuntos.md. Se activa con {@code app.adjuntos.almacen=r2}.
 *
 * <p>El bucket tiene que ser privado: los archivos nunca se sirven directo desde R2, siempre pasan por la API.
 */
@Component
@ConditionalOnProperty(name = "app.adjuntos.almacen", havingValue = "r2")
public class AlmacenEnR2 implements AlmacenArchivos {

    private final S3Client s3;
    private final String bucket;

    public AlmacenEnR2(AdjuntosProperties propiedades) {
        AdjuntosProperties.R2 r2 = propiedades.r2();
        if (r2 == null || vacio(r2.bucket()) || vacio(r2.endpoint()) || vacio(r2.accessKey())
            || vacio(r2.secretKey())) {
            throw new IllegalStateException(
                "Con ADJUNTOS_ALMACEN=r2 hacen falta ADJUNTOS_BUCKET, ADJUNTOS_ENDPOINT, "
                    + "ADJUNTOS_ACCESS_KEY y ADJUNTOS_SECRET_KEY");
        }
        this.bucket = r2.bucket();
        this.s3 = S3Client.builder()
            .endpointOverride(URI.create(r2.endpoint()))
            // R2 ignora la región, pero el SDK exige una: "auto" es el valor que indica Cloudflare
            .region(Region.of("auto"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(r2.accessKey(), r2.secretKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Override
    public void guardar(String clave, InputStream contenido) {
        Path temporal = null;
        try {
            // S3 necesita saber el largo antes de subir: se baja a un temporal (el tope es de pocos MB)
            temporal = Files.createTempFile("adjunto-", ".tmp");
            Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(clave).build(),
                RequestBody.fromFile(temporal));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + clave, e);
        } catch (SdkException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + clave, new IOException(e));
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
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(clave).build());
        } catch (SdkException e) {
            throw new UncheckedIOException("No se pudo abrir el archivo " + clave, new IOException(e));
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            // En S3 borrar una clave que no existe no es un error
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
        } catch (SdkException e) {
            throw new UncheckedIOException("No se pudo borrar el archivo " + clave, new IOException(e));
        }
    }

    @PreDestroy
    void cerrar() {
        s3.close();
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
