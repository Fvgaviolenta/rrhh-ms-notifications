package com.rrhh.notifications.controller;

import com.rrhh.notifications.dto.ApiResponse;
import com.rrhh.notifications.dto.response.NotificacionResponse;
import com.rrhh.notifications.dto.response.ServicioStatusResponse;
import com.rrhh.notifications.service.NotificacionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class NotificacionController {
    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping("/notificaciones")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<NotificacionResponse>> listar() {
        return ApiResponse.ok(notificacionService.listarMisNotificaciones(), "Notificaciones del usuario autenticado");
    }

    @GetMapping("/notificaciones/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ServicioStatusResponse> status() {
        return ApiResponse.ok(notificacionService.obtenerEstadoServicio(), "Estado del servicio de notificaciones");
    }
}
