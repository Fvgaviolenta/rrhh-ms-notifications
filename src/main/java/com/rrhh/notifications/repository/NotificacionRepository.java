package com.rrhh.notifications.repository;

import com.rrhh.notifications.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, String> {
    List<Notificacion> findByTenantIdAndDestinatarioIdOrderByCreadoEnDesc(String tenantId, String destinatarioId);

    long countByTenantIdAndEstado(String tenantId, String estado);
}
