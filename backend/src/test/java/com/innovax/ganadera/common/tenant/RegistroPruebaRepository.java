package com.innovax.ganadera.common.tenant;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroPruebaRepository extends JpaRepository<RegistroPrueba, UUID> {

    List<RegistroPrueba> findByNombre(String nombre);
}
