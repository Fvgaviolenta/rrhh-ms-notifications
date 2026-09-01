package com.rrhh.notifications.service;

import com.rrhh.notifications.exception.DomainException;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;

    private final TenantContext tenantContext = new TenantContext();

    private NotificacionService notificacionService;

    @BeforeEach
    void setUp() {
        notificacionService = new NotificacionService(notificacionRepository, tenantContext, "localhost", "localhost");
    }

    @AfterEach
    void cleanup() {
        tenantContext.clear();
    }

    @Test
    void listarFiltraPorTenantYDestinatario() {
        tenantContext.set(new TenantContext.AuthenticatedUser(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "admin.demo@rrhh.local",
                "Admin de RRHH",
                null,
                "sub"
        ));
        Notificacion notificacion = new Notificacion();
        notificacion.setId("11111111-aaaa-aaaa-aaaa-aaaaaaaa0001");
        notificacion.setTenantId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        notificacion.setDestinatarioId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        notificacion.setCanal("IN_APP");
        notificacion.setAsunto("Contrato creado");
        notificacion.setCuerpo("Detalle");
        notificacion.setEstado("PENDIENTE");
        notificacion.setCreadoEn(Instant.parse("2026-01-01T12:00:00Z"));

        when(notificacionRepository.findByTenantIdAndDestinatarioIdOrderByCreadoEnDesc(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        )).thenReturn(List.of(notificacion));

        var result = notificacionService.listarMisNotificaciones();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().asunto()).isEqualTo("Contrato creado");
    }

    @Test
    void listarSinUserIdFalla() {
        tenantContext.set(new TenantContext.AuthenticatedUser(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                null,
                "admin.demo@rrhh.local",
                "Admin de RRHH",
                null,
                "sub"
        ));

        assertThatThrownBy(() -> notificacionService.listarMisNotificaciones())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("user_id");
    }
}
