package com.rrhh.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Referencia de solo lectura a Identity para asociar el sujeto de Cognito con
 * el identificador interno usado como destinatario de notificaciones.
 */
@Getter
@Entity
@Table(name = "identity_usuario")
public class IdentityUsuarioReferencia {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "tenant_id", length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;

    @Column(name = "cognito_sub", length = 100)
    private String cognitoSub;
}
