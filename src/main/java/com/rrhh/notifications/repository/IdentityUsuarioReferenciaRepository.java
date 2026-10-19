package com.rrhh.notifications.repository;

import com.rrhh.notifications.model.IdentityUsuarioReferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface IdentityUsuarioReferenciaRepository extends JpaRepository<IdentityUsuarioReferencia, String> {
    Optional<IdentityUsuarioReferencia> findByTenantIdAndCognitoSub(String tenantId, String cognitoSub);
    Optional<IdentityUsuarioReferencia> findByTenantIdAndTrabajadorId(String tenantId, String trabajadorId);
    List<IdentityUsuarioReferencia> findByTenantIdAndRolIn(String tenantId, List<String> roles);
}
