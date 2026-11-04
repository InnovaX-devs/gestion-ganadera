package com.innovax.ganadera.sync;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.sync.dto.ResultadoOperacion;
import com.innovax.ganadera.sync.dto.SyncRequest;
import com.innovax.ganadera.sync.dto.SyncResponse;

/**
 * Sincronización con la app offline (#30): push de lo hecho sin conexión y pull de lo que cambió.
 * Lo puede usar cualquier miembro del establecimiento; cada operación del push controla su propio
 * permiso (ej: un socio no puede registrar tratamientos). Ver docs/sync.md.
 */
@Service
public class SyncService {

    private final SyncPushService push;
    private final SyncPullService pull;
    private final AutorizacionService autorizacion;
    private final EstablecimientoRepository establecimientoRepository;
    private final SyncProperties propiedades;
    private final ObjectMapper json;
    private final Clock reloj;

    SyncService(SyncPushService push, SyncPullService pull, AutorizacionService autorizacion,
                EstablecimientoRepository establecimientoRepository, SyncProperties propiedades, ObjectMapper json,
                Clock reloj) {
        this.push = push;
        this.pull = pull;
        this.autorizacion = autorizacion;
        this.establecimientoRepository = establecimientoRepository;
        this.propiedades = propiedades;
        this.json = json;
        this.reloj = reloj;
    }

    public SyncResponse sincronizar(SyncRequest request) {
        var est = request.establecimientoId();
        Rol rol = autorizacion.exigir(est, Permiso.VER);
        Establecimiento establecimiento = establecimientoRepository.findByIdAndActivoTrue(est)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", est));
        // Antes de aplicar nada: con un cursor inválido el cliente tiene que reintentar todo igual
        Cursor cursor = Cursor.decodificar(request.cursor(), est, json);

        List<ResultadoOperacion> resultados = push.aplicar(est, request.operacionesONada());

        int limite = request.limite() != null ? request.limite() : propiedades.limiteValido();
        var cambios = pull.traer(cursor, limite);

        return new SyncResponse(
            cambios.cursor().codificar(json),
            cambios.hayMas(),
            reloj.instant(),
            resultados,
            new SyncResponse.Establecimiento(establecimiento.getId(), establecimiento.getNombre(),
                establecimiento.getLocalidad(), establecimiento.getProvincia(), rol),
            cambios.cambios(),
            cambios.bajas());
    }
}
