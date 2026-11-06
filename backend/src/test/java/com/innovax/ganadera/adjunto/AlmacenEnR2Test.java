package com.innovax.ganadera.adjunto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** No se conecta a R2: solo comprueba la configuración. El resto se prueba a mano con un bucket real. */
class AlmacenEnR2Test {

    private static AdjuntosProperties conR2(AdjuntosProperties.R2 r2) {
        return new AdjuntosProperties("r2", "", DataSize.ofMegabytes(1), 3, r2);
    }

    @Test
    void sinConfiguracionNoArranca() {
        assertThatThrownBy(() -> new AlmacenEnR2(conR2(null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADJUNTOS_BUCKET");
        assertThatThrownBy(() -> new AlmacenEnR2(conR2(new AdjuntosProperties.R2("b", "", "k", "s"))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void conConfiguracionCompletaSeCreaSinConectarse() {
        var r2 = new AdjuntosProperties.R2("bucket", "https://cuenta.r2.cloudflarestorage.com", "clave", "secreto");
        AlmacenEnR2 almacen = new AlmacenEnR2(conR2(r2));
        assertThat(almacen).isNotNull();
        almacen.cerrar();
    }

    @Test
    void discoSinDirectorioNoArranca() {
        assertThatThrownBy(() -> new AlmacenEnDisco(
            new AdjuntosProperties("disco", " ", DataSize.ofMegabytes(1), 3, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADJUNTOS_DIR");
    }
}
