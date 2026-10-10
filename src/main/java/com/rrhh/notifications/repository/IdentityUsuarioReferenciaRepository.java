package com.rrhh.notifications.repository;

import com.rrhh.notifications.model.IdentityUsuarioReferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityUsuarioReferenciaRepository extends JpaRepository<IdentityUsuarioReferencia, String> {
    Optional<IdentityUsuarioReferencia> findByTenantIdAndCognitoSub(String tenantId, String cognitoSub);
}
