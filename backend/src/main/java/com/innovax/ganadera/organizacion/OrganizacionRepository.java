package com.innovax.ganadera.organizacion;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizacionRepository extends JpaRepository<Organizacion, UUID> {
}
