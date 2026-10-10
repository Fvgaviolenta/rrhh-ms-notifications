package com.rrhh.notifications.service;

import com.rrhh.notifications.dto.request.MarcarLeidaRequest;
import com.rrhh.notifications.dto.response.NotificacionResponse;
import com.rrhh.notifications.dto.response.ServicioStatusResponse;
import com.rrhh.notifications.exception.DomainException;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_LEIDO = "LEIDO";

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

    public List<NotificacionResponse> listarNoLeidas() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        if (actor.userId() == null || actor.userId().isBlank()) {
            throw new DomainException(400, "El token no incluye user_id");
        }
        return notificacionRepository
                .findByTenantIdAndDestinatarioIdAndEstadoOrderByCreadoEnDesc(
                        actor.tenantId(),
                        actor.userId(),
                        ESTADO_PENDIENTE
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificacionResponse marcarLeida(String id, MarcarLeidaRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();

        Notificacion notificacion = notificacionRepository
                .findByIdAndTenantIdAndDestinatarioId(id, actor.tenantId(), actor.userId())
                .orElseThrow(() -> new DomainException(404, "Notificación no encontrada"));

        if (request.leido()) {
            notificacion.setEstado(ESTADO_LEIDO);
        } else {
            notificacion.setEstado(ESTADO_PENDIENTE);
        }

        notificacionRepository.save(notificacion);

        return toResponse(notificacion);
    }

    public long contarNoLeidas() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        if (actor.userId() == null || actor.userId().isBlank()) {
            throw new DomainException(400, "El token no incluye user_id");
        }
        return notificacionRepository.countByTenantIdAndDestinatarioIdAndEstado(
                actor.tenantId(), actor.userId(), ESTADO_PENDIENTE
        );
    }

    public ServicioStatusResponse obtenerEstadoServicio() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        long pendientes = actor.userId() == null || actor.userId().isBlank()
                ? 0
                : notificacionRepository.countByTenantIdAndDestinatarioIdAndEstado(
                        actor.tenantId(), actor.userId(), ESTADO_PENDIENTE
                );
        return new ServicioStatusResponse(
                "rrhh-notifications",
                "OPERATIVO",
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
