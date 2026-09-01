package com.rrhh.notifications.dto.response;

import java.time.Instant;

public record NotificacionResponse(
        String id,
        String tenantId,
        String destinatarioId,
        String canal,
        String asunto,
        String cuerpo,
        String estado,
        Instant creadoEn
) {}
