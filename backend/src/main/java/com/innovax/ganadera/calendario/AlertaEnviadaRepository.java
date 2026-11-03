package com.innovax.ganadera.calendario;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaEnviadaRepository extends JpaRepository<AlertaEnviada, UUID> {

    boolean existsByEstablecimientoIdAndUsuarioIdAndFecha(UUID establecimientoId, UUID usuarioId, LocalDate fecha);
}
