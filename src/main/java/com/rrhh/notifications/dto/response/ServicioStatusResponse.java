package com.rrhh.notifications.dto.response;

public record ServicioStatusResponse(
        String servicio,
        String estado,
        String rabbitmqHost,
        String redisHost,
        long notificacionesPendientes
) {}
