package com.rrhh.notifications.repository;

import com.rrhh.notifications.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, String> {
    List<Notificacion> findByTenantIdAndDestinatarioIdOrderByCreadoEnDesc(String tenantId, String destinatarioId);

    long countByTenantIdAndDestinatarioIdAndEstado(String tenantId, String destinatarioId, String estado);

    Optional<Notificacion> findByIdAndTenantIdAndDestinatarioId(String id, String tenantId, String destinatarioId);

    List<Notificacion> findByTenantIdAndDestinatarioIdAndEstadoOrderByCreadoEnDesc(
            String tenantId,
            String destinatarioId,
            String estado
    );
}
