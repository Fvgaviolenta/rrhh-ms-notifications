package com.rrhh.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "notifications_notificacion")
public class Notificacion {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;

    @Column(name = "destinatario_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String destinatarioId;

    @Column(nullable = false, length = 30)
    private String canal;

    @Column(nullable = false)
    private String asunto;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
