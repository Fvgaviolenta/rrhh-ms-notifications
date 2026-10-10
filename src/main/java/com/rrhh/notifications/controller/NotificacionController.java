package com.rrhh.notifications.controller;

import com.rrhh.notifications.dto.ApiResponse;
import com.rrhh.notifications.dto.request.MarcarLeidaRequest;
import com.rrhh.notifications.dto.response.NotificacionResponse;
import com.rrhh.notifications.dto.response.ServicioStatusResponse;
import com.rrhh.notifications.service.NotificacionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/notificaciones/no-leidas")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<NotificacionResponse>> noLeidas() {
        return ApiResponse.ok(notificacionService.listarNoLeidas(), "Notificaciones no leídas");
    }

    @GetMapping("/notificaciones/no-leidas/count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> contarNoLeidas() {
        return notificacionService.contarNoLeidas();
    }

    @PatchMapping("/notificaciones/{id}/marcar-leida")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NotificacionResponse> marcarLeida(
            @PathVariable("id") String id,
            @Valid @RequestBody MarcarLeidaRequest request
    ) {
        return ApiResponse.ok(notificacionService.marcarLeida(id, request), "Notificación actualizada");
    }

    @GetMapping("/notificaciones/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ServicioStatusResponse> status() {
        return ApiResponse.ok(notificacionService.obtenerEstadoServicio(), "Estado del servicio de notificaciones");
    }
}
