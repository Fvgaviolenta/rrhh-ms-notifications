package com.rrhh.notifications.repository;

import com.rrhh.notifications.model.TrabajadorReferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrabajadorReferenciaRepository extends JpaRepository<TrabajadorReferencia, String> {
    Optional<TrabajadorReferencia> findByIdAndTenantId(String id, String tenantId);
}
