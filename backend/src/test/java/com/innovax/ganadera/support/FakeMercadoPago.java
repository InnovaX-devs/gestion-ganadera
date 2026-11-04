package com.innovax.ganadera.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.innovax.ganadera.cobro.MercadoPago;
import com.innovax.ganadera.cobro.MercadoPagoException;

/**
 * Reemplaza a Mercado Pago en los tests. Guarda las suscripciones creadas y deja simular lo que
 * haría el cliente o Mercado Pago: autorizar, cobrar, rechazar, cancelar.
 */
@Component
@Primary
public class FakeMercadoPago implements MercadoPago {

    private final Map<String, Preapproval> preapprovals = new ConcurrentHashMap<>();
    private final Map<String, Cuota> cuotas = new ConcurrentHashMap<>();
    private final List<NuevaSuscripcion> creadas = new ArrayList<>();
    private final List<String> canceladas = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger();
    private final AtomicInteger secuenciaCobros = new AtomicInteger();
    private volatile boolean caido;

    public void limpiar() {
        preapprovals.clear();
        cuotas.clear();
        creadas.clear();
        canceladas.clear();
        caido = false;
    }

    // ---------- Simulación ----------

    /** El cliente pagó en Mercado Pago: la suscripción queda autorizada. */
    public void autorizar(String preapprovalId) {
        cambiar(preapprovalId, "authorized");
    }

    public void cambiar(String preapprovalId, String status) {
        Preapproval p = preapprovals.get(preapprovalId);
        preapprovals.put(preapprovalId, new Preapproval(p.id(), status, p.referencia(), p.initPoint()));
    }

    /** Un cobro mensual; devuelve el id del cobro (para el aviso subscription_authorized_payment). */
    public String cobrar(String preapprovalId, LocalDate fecha, boolean aprobado) {
        String id = String.valueOf(1000 + secuenciaCobros.incrementAndGet());
        cuotas.put(id, new Cuota(id, preapprovalId, fecha, aprobado));
        return id;
    }

    public void caer(boolean caido) {
        this.caido = caido;
    }

    public NuevaSuscripcion ultimaCreada() {
        return creadas.getLast();
    }

    public String ultimoId() {
        return "pre-" + secuencia.get();
    }

    public List<String> canceladas() {
        return List.copyOf(canceladas);
    }

    public String estado(String preapprovalId) {
        return preapprovals.get(preapprovalId).status();
    }

    // ---------- MercadoPago ----------

    @Override
    public Preapproval crear(NuevaSuscripcion s) {
        siCaido();
        creadas.add(s);
        String id = "pre-" + secuencia.incrementAndGet();
        Preapproval p = new Preapproval(id, "pending", s.referencia(), "https://mp.test/checkout?preapproval_id=" + id);
        preapprovals.put(id, p);
        return p;
    }

    @Override
    public Preapproval obtener(String preapprovalId) {
        siCaido();
        Preapproval p = preapprovals.get(preapprovalId);
        if (p == null) throw new MercadoPagoException("No existe " + preapprovalId, null);
        return p;
    }

    @Override
    public void cancelar(String preapprovalId) {
        siCaido();
        canceladas.add(preapprovalId);
        if (preapprovals.containsKey(preapprovalId)) cambiar(preapprovalId, "cancelled");
    }

    @Override
    public Cuota cuota(String cuotaId) {
        siCaido();
        return cuotas.get(cuotaId);
    }

    @Override
    public List<Cuota> cuotas(String preapprovalId) {
        siCaido();
        return cuotas.values().stream().filter(c -> c.preapprovalId().equals(preapprovalId)).toList();
    }

    private void siCaido() {
        if (caido) throw new MercadoPagoException("Mercado Pago caído (simulado)", null);
    }
}
