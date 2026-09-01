package com.rrhh.notifications.service;

import com.rrhh.notifications.dto.response.NotificacionResponse;
import com.rrhh.notifications.dto.response.ServicioStatusResponse;
import com.rrhh.notifications.exception.DomainException;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificacionService {
    private static final String ESTADO_PENDIENTE = "PENDIENTE";

    private final NotificacionRepository notificacionRepository;
    private final TenantContext tenantContext;
    private final String rabbitmqHost;
    private final String redisHost;

    public NotificacionService(
            NotificacionRepository notificacionRepository,
            TenantContext tenantContext,
            @Value("${spring.rabbitmq.host:localhost}") String rabbitmqHost,
            @Value("${spring.data.redis.host:localhost}") String redisHost
    ) {
        this.notificacionRepository = notificacionRepository;
        this.tenantContext = tenantContext;
        this.rabbitmqHost = rabbitmqHost;
        this.redisHost = redisHost;
    }

    public List<NotificacionResponse> listarMisNotificaciones() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        if (actor.userId() == null || actor.userId().isBlank()) {
            throw new DomainException(400, "El token no incluye user_id");
        }
        return notificacionRepository
                .findByTenantIdAndDestinatarioIdOrderByCreadoEnDesc(actor.tenantId(), actor.userId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ServicioStatusResponse obtenerEstadoServicio() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        long pendientes = notificacionRepository.countByTenantIdAndEstado(actor.tenantId(), ESTADO_PENDIENTE);
        return new ServicioStatusResponse(
                "rrhh-notifications",
                "UP",
                rabbitmqHost,
                redisHost,
                pendientes
        );
    }

    private NotificacionResponse toResponse(Notificacion notificacion) {
        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getTenantId(),
                notificacion.getDestinatarioId(),
                notificacion.getCanal(),
                notificacion.getAsunto(),
                notificacion.getCuerpo(),
                notificacion.getEstado(),
                notificacion.getCreadoEn()
        );
    }
}
