package com.rrhh.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * Referencia de solo lectura a trabajadores para el aviso de atraso (S5).
 * Se consulta siempre filtrando por {@code tenant_id}.
 */
@Getter
@Entity
@Immutable
@Table(name = "trabajadores_trabajador")
public class TrabajadorReferencia {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "tenant_id", length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;

    @Column(length = 120)
    private String nombre;

    @Column(length = 120)
    private String apellido;

    @Column(name = "jefatura_id", length = 36, columnDefinition = "CHAR(36)")
    private String jefaturaId;
}
