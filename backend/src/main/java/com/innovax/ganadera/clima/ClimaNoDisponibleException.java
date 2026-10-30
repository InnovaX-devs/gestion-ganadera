package com.innovax.ganadera.clima;

/** Open-Meteo no respondió y no hay un pronóstico reciente guardado. Responde 503. */
public class ClimaNoDisponibleException extends RuntimeException {

    public ClimaNoDisponibleException() {
        super("El servicio de clima no responde. Probá de nuevo en unos minutos.");
    }
}
