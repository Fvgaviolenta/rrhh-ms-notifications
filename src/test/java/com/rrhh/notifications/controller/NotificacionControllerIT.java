package com.rrhh.notifications.controller;

import com.rrhh.notifications.NotificationsApplication;
import com.rrhh.notifications.config.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NotificationsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class NotificacionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usuarioListaSoloSusNotificaciones() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.length()").value(2))
                .andExpect(jsonPath("$.datos[0].destinatario_id").value("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    }

    @Test
    void otroUsuarioNoVeNotificacionesDelAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones").with(otroUsuarioJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.length()").value(1))
                .andExpect(jsonPath("$.datos[0].destinatario_id").value("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    }

    @Test
    void statusRetornaEstadoDelServicio() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones/status").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.servicio").value("rrhh-notifications"))
                .andExpect(jsonPath("$.datos.estado").value("UP"))
                .andExpect(jsonPath("$.datos.notificaciones_pendientes").value(2));
    }

    @Test
    void sinJwtRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones"))
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
