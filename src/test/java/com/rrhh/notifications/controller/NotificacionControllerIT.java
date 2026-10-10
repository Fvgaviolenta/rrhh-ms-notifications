package com.rrhh.notifications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.notifications.NotificationsApplication;
import com.rrhh.notifications.config.TestJwtConfig;
import com.rrhh.notifications.dto.request.MarcarLeidaRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = NotificationsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class NotificacionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void usuarioListaSoloSusNotificaciones() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    void listarNoLeidas() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones/no-leidas").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    void contarNoLeidas() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones/no-leidas/count").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());
    }

    @Test
    void marcarComoLeida() throws Exception {
        MarcarLeidaRequest request = new MarcarLeidaRequest(true);

        mockMvc.perform(patch("/api/v1/notificaciones/test-notificacion-id/marcar-leida")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Estado de notificación actualizado"));
    }

    @Test
    void marcarComoNoLeida() throws Exception {
        MarcarLeidaRequest request = new MarcarLeidaRequest(false);

        mockMvc.perform(patch("/api/v1/notificaciones/test-notificacion-id/marcar-leida")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void otroUsuarioNoVeNotificacionesDelAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones").with(otroUsuarioJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    void statusRetornaEstadoDelServicio() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones/status").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.servicio").value("rrhh-notifications"))
                .andExpect(jsonPath("$.datos.estado").value("OPERATIVO"));
    }

    @Test
    void sinJwtRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void marcarLeidaSinToken() throws Exception {
        MarcarLeidaRequest request = new MarcarLeidaRequest(true);

        mockMvc.perform(patch("/api/v1/notificaciones/test-notificacion-id/marcar-leida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(b -> b.subject("a")
                .claim("email", "admin.demo@rrhh.local")
                .claim("custom:tenant_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("custom:role", "Admin de RRHH")
                .claim("custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    }

    private static RequestPostProcessor otroUsuarioJwt() {
        return jwt().jwt(b -> b.subject("c")
                .claim("email", "otro@rrhh.local")
                .claim("custom:tenant_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("custom:role", "Trabajador")
                .claim("custom:user_id", "cccccccc-cccc-cccc-cccc-cccccccccccc"));
    }
}
