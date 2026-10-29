package com.innovax.ganadera.evento.dto;

import java.util.List;
import java.util.UUID;

/** Resultado de registrar un evento: la operación y un evento por cada animal (o uno solo). */
public record OperacionResponse(UUID operacionId, int cantidad, List<EventoResponse> eventos) {
}
