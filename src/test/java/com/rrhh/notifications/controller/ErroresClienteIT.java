package com.rrhh.notifications.controller;

import com.rrhh.notifications.NotificationsApplication;
import com.rrhh.notifications.config.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Errores causados por el cliente: deben responder 4xx con el formato ApiResponse, no 500. */
@SpringBootTest(classes = NotificationsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class ErroresClienteIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rutaSinControladorResponde404() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.mensaje").value("Recurso no encontrado"));
    }

    @Test
    void metodoNoPermitidoResponde405ConAllow() throws Exception {
        mockMvc.perform(delete("/api/v1/notificaciones").with(adminJwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.codigo").value(405))
                .andExpect(jsonPath("$.mensaje").value("Método no permitido"));
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        mockMvc.perform(patch("/api/v1/notificaciones/11111111-aaaa-aaaa-aaaa-aaaaaaaa0001/marcar-leida").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campo\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje").value("Solicitud inválida"));
    }

    @Test
    void tipoDeContenidoNoSoportadoResponde415() throws Exception {
        mockMvc.perform(patch("/api/v1/notificaciones/11111111-aaaa-aaaa-aaaa-aaaaaaaa0001/marcar-leida").with(adminJwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value(415));
    }

    @Test
    void validacionFallidaMantieneLosErroresPorCampo() throws Exception {
        mockMvc.perform(patch("/api/v1/notificaciones/11111111-aaaa-aaaa-aaaa-aaaaaaaa0001/marcar-leida").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Validación fallida"))
                .andExpect(jsonPath("$.errores").isNotEmpty());
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(b -> b.subject("a")
                .claim("email", "admin.demo@rrhh.local")
                .claim("custom:tenant_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("custom:role", "Admin de RRHH")
                .claim("custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    }
}
