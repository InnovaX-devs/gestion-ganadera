package com.innovax.ganadera.animal.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;

/** Issue #16: cómo el servicio aplica las reglas al animal y qué deja en el historial. Sin base. */
class CategoriaServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);

    private CambioCategoriaRepository historial;
    private AnimalRepository animalRepository;
    private CategoriaService service;

    @BeforeEach
    void preparar() {
        historial = mock(CambioCategoriaRepository.class);
        animalRepository = mock(AnimalRepository.class);
        when(historial.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Clock reloj = Clock.fixed(HOY.atStartOfDay(ZoneId.of(CategoriaConfig.ZONA_HORARIA)).toInstant(),
            ZoneId.of(CategoriaConfig.ZONA_HORARIA));
        service = new CategoriaService(new ReglasCategoria(ParametrosCategoria.POR_DEFECTO), historial,
            animalRepository, mock(EstablecimientoRepository.class), mock(AutorizacionService.class), reloj);
    }

    @Test
    void cambiaLaCategoriaYLoDejaEnElHistorial() {
        Animal vaquillona = animal(Sexo.HEMBRA, Categoria.VAQUILLONA, null);

        List<CambioCategoria> cambios = service.aplicar(vaquillona, new Hecho.Parto(HOY.minusDays(2)));

        assertThat(vaquillona.getCategoria()).isEqualTo(Categoria.VACA);
        assertThat(cambios).singleElement().satisfies(c -> {
            assertThat(c.getAnimal()).isSameAs(vaquillona);
            assertThat(c.getCategoriaAnterior()).isEqualTo(Categoria.VAQUILLONA);
            assertThat(c.getCategoriaNueva()).isEqualTo(Categoria.VACA);
            assertThat(c.getMotivo()).isEqualTo(MotivoCambio.PARTO);
            assertThat(c.getFecha()).isEqualTo(HOY.minusDays(2));
            assertThat(c.getUsuarioId()).isNull();
        });
        verify(historial).save(any());
    }

    @Test
    void siNoCorrespondeNingunCambioNoGuardaNada() {
        Animal vaca = animal(Sexo.HEMBRA, Categoria.VACA, null);

        assertThat(service.aplicar(vaca, new Hecho.Parto(HOY))).isEmpty();
        assertThat(vaca.getCategoria()).isEqualTo(Categoria.VACA);
        verify(historial, never()).save(any());
    }

    @Test
    void unAnimalAtrasadoAvanzaVariasCategoriasConUnaFilaPorPaso() {
        Animal ternero = animal(Sexo.MACHO, Categoria.TERNERO, HOY.minusMonths(30));

        List<CambioCategoria> cambios = service.aplicar(ternero, new Hecho.PasoDelTiempo(HOY));

        assertThat(ternero.getCategoria()).isEqualTo(Categoria.TORO);
        assertThat(cambios).extracting(CambioCategoria::getCategoriaAnterior, CambioCategoria::getCategoriaNueva)
            .containsExactly(
                tuple(Categoria.TERNERO, Categoria.TORITO),
                tuple(Categoria.TORITO, Categoria.TORO));
        assertThat(cambios).allSatisfy(c -> assertThat(c.getMotivo()).isEqualTo(MotivoCambio.EDAD));
    }

    @Test
    void elDesteteNoArrastraCambiosPorEdad() {
        // Tiene edad de toro, pero el destete solo lo pasa a torito: lo demás lo hace el job
        Animal ternero = animal(Sexo.MACHO, Categoria.TERNERO, HOY.minusMonths(30));

        service.aplicar(ternero, new Hecho.Destete(HOY));

        assertThat(ternero.getCategoria()).isEqualTo(Categoria.TORITO);
    }

    @Test
    void elTerneroCastradoPasaANovillitoYQuedaCastrado() {
        Animal ternero = animal(Sexo.MACHO, Categoria.TERNERO, null);
        ternero.setCastrado(true);

        service.aplicar(ternero, new Hecho.Destete(HOY));

        assertThat(ternero.getCategoria()).isEqualTo(Categoria.NOVILLITO);
        assertThat(ternero.isCastrado()).isTrue();
    }

    @Test
    void elPesajeCambiaPorPeso() {
        Animal torito = animal(Sexo.MACHO, Categoria.TORITO, null);

        service.aplicar(torito, new Hecho.Pesaje(HOY, new BigDecimal("470")));

        assertThat(torito.getCategoria()).isEqualTo(Categoria.TORO);
    }

    @Test
    void unAnimalQueNoEstaActivoNoCambia() {
        Animal vendido = animal(Sexo.HEMBRA, Categoria.TERNERA, HOY.minusYears(1));
        vendido.setEstado(EstadoAnimal.VENDIDO);

        assertThat(service.aplicar(vendido, new Hecho.PasoDelTiempo(HOY))).isEmpty();
        assertThat(vendido.getCategoria()).isEqualTo(Categoria.TERNERA);
    }

    @Test
    void recalcularPorEdadBuscaSoloLosCandidatosYCuentaLosQueCambiaron() {
        Animal ternera = animal(Sexo.HEMBRA, Categoria.TERNERA, HOY.minusMonths(9));
        Animal torito = animal(Sexo.MACHO, Categoria.TORITO, HOY.minusMonths(10));
        when(animalRepository.candidatosACambioPorEdad(eq(EstadoAnimal.ACTIVO),
            eq(CategoriaService.CAMBIAN_POR_EDAD), eq(HOY.minusMonths(8))))
            .thenReturn(List.of(ternera, torito));

        assertThat(service.recalcularPorEdad(HOY)).isEqualTo(1);
        assertThat(ternera.getCategoria()).isEqualTo(Categoria.VAQUILLONA);
        assertThat(torito.getCategoria()).isEqualTo(Categoria.TORITO);
    }

    // ---------- Revertir un parto (#18) ----------

    @Test
    void revertirUnPartoVuelveALaCategoriaAnteriorYBorraElHistorial() {
        Animal vaca = animal(Sexo.HEMBRA, Categoria.VACA, null);
        var cambio = new CambioCategoria(vaca, Categoria.VAQUILLONA, Categoria.VACA, MotivoCambio.PARTO, HOY, null);
        when(historial.findByAnimalIdAndMotivoAndFechaOrderByCreatedAtAsc(vaca.getId(), MotivoCambio.PARTO, HOY))
            .thenReturn(List.of(cambio));

        service.revertirParto(vaca, HOY);

        assertThat(vaca.getCategoria()).isEqualTo(Categoria.VAQUILLONA);
        verify(historial).deleteAll(List.of(cambio));
    }

    @Test
    void siDespuesLaCambiaronAManoRevertirElPartoNoLaToca() {
        Animal hembra = animal(Sexo.HEMBRA, Categoria.VACA, null);
        var cambio = new CambioCategoria(hembra, Categoria.VAQUILLONA, Categoria.VACA, MotivoCambio.PARTO, HOY, null);
        when(historial.findByAnimalIdAndMotivoAndFechaOrderByCreatedAtAsc(hembra.getId(), MotivoCambio.PARTO, HOY))
            .thenReturn(List.of(cambio));
        hembra.setCategoria(Categoria.TERNERA); // edición manual posterior al parto

        service.revertirParto(hembra, HOY);

        assertThat(hembra.getCategoria()).isEqualTo(Categoria.TERNERA);
        verify(historial, never()).deleteAll(any());
    }

    @Test
    void siElPartoNoCambioLaCategoriaRevertirloNoHaceNada() {
        Animal vaca = animal(Sexo.HEMBRA, Categoria.VACA, null);
        when(historial.findByAnimalIdAndMotivoAndFechaOrderByCreatedAtAsc(any(), any(), any())).thenReturn(List.of());

        service.revertirParto(vaca, HOY);

        assertThat(vaca.getCategoria()).isEqualTo(Categoria.VACA);
        verify(historial, never()).deleteAll(any());
    }

    private static Animal animal(Sexo sexo, Categoria categoria, LocalDate nacimiento) {
        Animal animal = new Animal(new Establecimiento("La Esperanza"), "A-1", sexo, categoria);
        animal.setFechaNacimiento(nacimiento);
        return animal;
    }
}
