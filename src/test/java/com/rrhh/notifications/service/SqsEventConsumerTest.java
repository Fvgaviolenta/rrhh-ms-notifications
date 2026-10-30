package com.rrhh.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.notifications.model.IdentityUsuarioReferencia;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.repository.IdentityUsuarioReferenciaRepository;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsEventConsumerTest {

    @Mock
    private ObjectProvider<SqsClient> sqs;

    @Mock
    private NotificacionRepository notifications;

    @Mock
    private ProcessedEventRepository processed;

    @Mock
    private IdentityUsuarioReferenciaRepository users;

    private SqsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SqsEventConsumer(sqs, new ObjectMapper(), notifications, processed, users, false, "");
    }

    private String evento(String nombre) {
        return """
                {"eventId":"e-1","eventType":"acceso.solicitado","tenantId":"t-1","aggregateId":"u-9",
                 "payload":{"usuarioId":"u-9","email":"pablo@gmail.com"%s}}"""
                .formatted(nombre == null ? "" : ",\"nombre\":\"" + nombre + "\"");
    }

    private void unAdminEnElTenant() {
        IdentityUsuarioReferencia admin = mock(IdentityUsuarioReferencia.class);
        when(admin.getId()).thenReturn("adm-1");
        when(users.findByTenantIdAndRolIn(eq("t-1"), anyList())).thenReturn(List.of(admin));
    }

    private Notificacion notificacionGuardada() {
        ArgumentCaptor<Notificacion> n = ArgumentCaptor.forClass(Notificacion.class);
        verify(notifications).save(n.capture());
        return n.getValue();
    }

    @Test
    void accesoSolicitadoNotificaAAdminsDelTenant() throws Exception {
        when(processed.existsById("e-1")).thenReturn(false);
        unAdminEnElTenant();

        consumer.procesar(evento("Pablo Postulante"));

        Notificacion n = notificacionGuardada();
        assertEquals("Nueva solicitud de acceso", n.getAsunto());
        assertEquals("Pablo Postulante solicitó acceso a la empresa.", n.getCuerpo());
        assertEquals("acceso.solicitado", n.getTipoEvento());
        assertEquals("u-9", n.getReferenciaId());
        assertEquals("adm-1", n.getDestinatarioId());
        assertEquals("t-1", n.getTenantId());
        verify(users).findByTenantIdAndRolIn("t-1", List.of("ADMIN_RRHH", "Admin de RRHH", "SUPERADMIN", "SuperAdmin"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void accesoSolicitadoSinNombreUsaEmail(String nombre) throws Exception {
        unAdminEnElTenant();

        consumer.procesar(evento(nombre));

        assertEquals("pablo@gmail.com solicitó acceso a la empresa.", notificacionGuardada().getCuerpo());
    }

    @Test
    void accesoSolicitadoConNombreNullJsonUsaEmail() throws Exception {
        unAdminEnElTenant();

        consumer.procesar("""
                {"eventId":"e-1","eventType":"acceso.solicitado","tenantId":"t-1","aggregateId":"u-9",
                 "payload":{"usuarioId":"u-9","email":"pablo@gmail.com","nombre":null}}""");

        assertEquals("pablo@gmail.com solicitó acceso a la empresa.", notificacionGuardada().getCuerpo());
    }
}
