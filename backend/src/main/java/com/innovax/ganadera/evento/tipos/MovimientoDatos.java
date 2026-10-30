package com.innovax.ganadera.evento.tipos;

import java.util.UUID;

/**
 * Issue #22. Un movimiento cambia al animal de lote, de rodeo o de establecimiento.
 *
 * <p>El cliente manda solo el destino; {@code desde} y {@code hacia} los completa el servidor (se
 * ignora lo que mande el cliente). Quedan guardados por animal, así el historial dice de dónde a
 * dónde se movió aunque después se renombren o se den de baja los lotes, y se puede deshacer.
 *
 * <p>Qué cambia depende de lo que se indique:
 * <ul>
 *   <li><b>Mismo establecimiento</b>: solo cambian los campos indicados. Un lote o rodeo que no se
 *       indica se deja como está; con {@code quitarLote} / {@code quitarRodeo} el animal queda sin
 *       asignar.</li>
 *   <li><b>Otro establecimiento de la organización</b> ({@code establecimientoDestinoId}): el lote y
 *       el rodeo de origen no existen allá, así que el animal queda en los indicados (que tienen que
 *       ser del destino) o sin asignar.</li>
 * </ul>
 *
 * @param establecimientoDestinoId opcional. Si es el mismo del que salen los animales, no cambia nada.
 * @param loteDestinoId            opcional. Lote al que van (del establecimiento de destino).
 * @param rodeoDestinoId           opcional. Rodeo al que van (del establecimiento de destino).
 * @param quitarLote               true para dejar al animal sin lote. No se combina con {@code loteDestinoId}.
 * @param quitarRodeo              true para dejar al animal sin rodeo. No se combina con {@code rodeoDestinoId}.
 * @param desde                    dónde estaba el animal (lo completa el servidor)
 * @param hacia                    dónde queda el animal (lo completa el servidor)
 */
public record MovimientoDatos(
    UUID establecimientoDestinoId,
    UUID loteDestinoId,
    UUID rodeoDestinoId,
    boolean quitarLote,
    boolean quitarRodeo,
    Ubicacion desde,
    Ubicacion hacia
) {

    /** Un establecimiento, lote o rodeo con el nombre que tenía al momento del movimiento. */
    public record Ref(UUID id, String nombre) {
    }

    /** Dónde está (o estaba) un animal. Lote o rodeo en null: sin asignar. */
    public record Ubicacion(Ref establecimiento, Ref lote, Ref rodeo) {
    }

    /** Copia con el destino pedido ya resuelto contra la base (todavía sin saber de dónde sale cada animal). */
    MovimientoDatos conDestino(Ubicacion destino) {
        return new MovimientoDatos(establecimientoDestinoId, loteDestinoId, rodeoDestinoId,
            quitarLote, quitarRodeo, null, destino);
    }

    /** Copia con el recorrido de un animal: de dónde sale y dónde queda. */
    MovimientoDatos conRecorrido(Ubicacion desde, Ubicacion hacia) {
        return new MovimientoDatos(establecimientoDestinoId, loteDestinoId, rodeoDestinoId,
            quitarLote, quitarRodeo, desde, hacia);
    }
}
