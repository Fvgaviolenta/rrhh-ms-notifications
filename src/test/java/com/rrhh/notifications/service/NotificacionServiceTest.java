package com.rrhh.notifications.service;

import com.rrhh.notifications.exception.DomainException;
import com.rrhh.notifications.model.IdentityUsuarioReferencia;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.repository.IdentityUsuarioReferenciaRepository;
import com.rrhh.notifications.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;

    @Mock
    private IdentityUsuarioReferenciaRepository identityUsuarioReferenciaRepository;

    private final TenantContext tenantContext = new TenantContext();

    private NotificacionService notificacionService;

    @BeforeEach
    void setUp() {
        notificacionService = new NotificacionService(
                notificacionRepository, identityUsuarioReferenciaRepository, tenantContext, "localhost", "localhost");
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
    void listarSinUserIdNiSubFalla() {
        tenantContext.set(new TenantContext.AuthenticatedUser(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                null,
                "admin.demo@rrhh.local",
                "Admin de RRHH",
                null,
                null
        ));

        assertThatThrownBy(() -> notificacionService.listarMisNotificaciones())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("identificador de usuario");
    }

    @Test
    void listarResuelveUsuarioInternoDesdeElSubDeCognito() {
        String tenantId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String userId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        tenantContext.set(new TenantContext.AuthenticatedUser(
                tenantId, null, "admin@rrhh.local", "Admin de RRHH", null, "cognito-sub"));
        IdentityUsuarioReferencia referencia = mock(IdentityUsuarioReferencia.class);
        when(referencia.getId()).thenReturn(userId);
        when(identityUsuarioReferenciaRepository.findByTenantIdAndCognitoSub(tenantId, "cognito-sub"))
                .thenReturn(Optional.of(referencia));
        when(notificacionRepository.findByTenantIdAndDestinatarioIdOrderByCreadoEnDesc(tenantId, userId))
                .thenReturn(List.of());

        assertThat(notificacionService.listarMisNotificaciones()).isEmpty();
    }
}
