package com.rrhh.notifications.service;

import com.rrhh.notifications.dto.request.MarcarLeidaRequest;
import com.rrhh.notifications.dto.response.NotificacionResponse;
import com.rrhh.notifications.dto.response.ServicioStatusResponse;
import com.rrhh.notifications.exception.DomainException;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.repository.IdentityUsuarioReferenciaRepository;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class NotificacionService {
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_LEIDA = "LEIDA";

    private final NotificacionRepository notificacionRepository;
    private final IdentityUsuarioReferenciaRepository identityUsuarioReferenciaRepository;
    private final TenantContext tenantContext;
    private final String rabbitmqHost;
    private final String redisHost;

    public NotificacionService(
            NotificacionRepository notificacionRepository,
            IdentityUsuarioReferenciaRepository identityUsuarioReferenciaRepository,
            TenantContext tenantContext,
            @Value("${spring.rabbitmq.host:localhost}") String rabbitmqHost,
            @Value("${spring.data.redis.host:localhost}") String redisHost
    ) {
        this.notificacionRepository = notificacionRepository;
        this.identityUsuarioReferenciaRepository = identityUsuarioReferenciaRepository;
        this.tenantContext = tenantContext;
        this.rabbitmqHost = rabbitmqHost;
        this.redisHost = redisHost;
    }

    public List<NotificacionResponse> listarMisNotificaciones() {
        TenantContext.AuthenticatedUser actor = destinatario();
        return notificacionRepository
                .findByTenantIdAndDestinatarioIdOrderByCreadoEnDesc(actor.tenantId(), actor.userId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<NotificacionResponse> listarPorTenantPlataforma(String tenantId) {
        exigirOperadorSaas();
        return notificacionRepository.findByTenantIdOrderByCreadoEnDesc(tenantId)
                .stream().map(this::toResponse).toList();
    }

    public List<NotificacionResponse> listarNoLeidas() {
        TenantContext.AuthenticatedUser actor = destinatario();
        return notificacionRepository
                .findByTenantIdAndDestinatarioIdAndEstadoNotOrderByCreadoEnDesc(actor.tenantId(), actor.userId(), ESTADO_LEIDA)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Map<String, Long> contarNoLeidas() {
        TenantContext.AuthenticatedUser actor = destinatario();
        long count = notificacionRepository.countByTenantIdAndDestinatarioIdAndEstadoNot(
                actor.tenantId(), actor.userId(), ESTADO_LEIDA);
        return Map.of("count", count);
    }

    @Transactional
    public NotificacionResponse marcarLeida(String id, MarcarLeidaRequest request) {
        TenantContext.AuthenticatedUser actor = destinatario();
        Notificacion notificacion = notificacionRepository
                .findByIdAndTenantIdAndDestinatarioId(id, actor.tenantId(), actor.userId())
                .orElseThrow(() -> new DomainException(404, "Notificación no encontrada"));
        notificacion.setEstado(request.leido() ? ESTADO_LEIDA : ESTADO_PENDIENTE);
        return toResponse(notificacionRepository.save(notificacion));
    }

    private TenantContext.AuthenticatedUser destinatario() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        if (actor.userId() != null && !actor.userId().isBlank()) {
            return actor;
        }
        if (actor.cognitoSub() == null || actor.cognitoSub().isBlank()) {
            throw new DomainException(400, "El token no incluye un identificador de usuario");
        }
        String userId = identityUsuarioReferenciaRepository
                .findByTenantIdAndCognitoSub(actor.tenantId(), actor.cognitoSub())
                .map(referencia -> referencia.getId())
                .orElseThrow(() -> new DomainException(404, "No existe una cuenta interna asociada al usuario autenticado"));
        return new TenantContext.AuthenticatedUser(
                actor.tenantId(), userId, actor.email(), actor.role(), actor.trabajadorId(), actor.cognitoSub());
    }

    private void exigirOperadorSaas() {
        if (!com.rrhh.notifications.security.Roles.OPERADOR_SAAS.equals(
                com.rrhh.notifications.security.Roles.authorityFromClaim(tenantContext.current().role()))) {
            throw new DomainException(403, "Acceso exclusivo para el operador SaaS");
        }
    }

    public ServicioStatusResponse obtenerEstadoServicio() {
        TenantContext.AuthenticatedUser actor = destinatario();
        long pendientes = notificacionRepository.countByTenantIdAndDestinatarioIdAndEstadoNot(
                actor.tenantId(), actor.userId(), ESTADO_LEIDA);
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
