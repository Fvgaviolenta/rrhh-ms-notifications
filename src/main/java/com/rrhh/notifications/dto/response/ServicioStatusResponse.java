package com.rrhh.notifications.dto.response;

public record ServicioStatusResponse(
        String servicio,
        String estado,
        String sqsEventsQueueUrl,
        String redisHost,
        long notificacionesPendientes
) {}
