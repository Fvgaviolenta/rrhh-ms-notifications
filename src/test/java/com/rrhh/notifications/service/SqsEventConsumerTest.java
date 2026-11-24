package com.rrhh.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.notifications.model.IdentityUsuarioReferencia;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.model.TrabajadorReferencia;
import com.rrhh.notifications.repository.IdentityUsuarioReferenciaRepository;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.repository.ProcessedEventRepository;
import com.rrhh.notifications.repository.TrabajadorReferenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @Mock
    private TrabajadorReferenciaRepository trabajadores;

    @Mock
    private PlatformTransactionManager transacciones;

    @Mock
    private CorreoSnsPublisher correo;

    private static final List<String> ADMINS = List.of("ADMIN_RRHH", "Admin de RRHH", "SUPERADMIN", "SuperAdmin");

    private SqsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SqsEventConsumer(sqs, new ObjectMapper(), notifications, processed, users, trabajadores, new TransactionTemplate(transacciones), false, "", correo);
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

    /**
     * Arma un aviso de atraso; {@code reemplazo} (p. ej. {@code "minutosAtraso":"12"})
     * sustituye el campo del payload con la misma clave.
     */
    private String atraso(String eventId, String fechaHora, String reemplazo) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("trabajadorId", "\"trabajadorId\":\"trab-1\"");
        payload.put("fechaHora", "\"fechaHora\":\"" + fechaHora + "\"");
        payload.put("turnoHoraInicio", "\"turnoHoraInicio\":\"08:00\"");
        payload.put("minutosAtraso", "\"minutosAtraso\":12");
        payload.put("zona", "\"zona\":\"America/Santiago\"");
        if (!reemplazo.isEmpty()) {
            String clave = reemplazo.substring(1, reemplazo.indexOf('"', 1));
            payload.put(clave, reemplazo);
        }
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"asistencia.atraso.detectado\",\"tenantId\":\"t-1\","
                + "\"aggregateId\":\"m-1\",\"payload\":{" + String.join(",", payload.values()) + "}}";
    }

    private TrabajadorReferencia ficha(String nombre, String apellido, String jefaturaId) {
        TrabajadorReferencia t = mock(TrabajadorReferencia.class);
        lenient().when(t.getNombre()).thenReturn(nombre);
        lenient().when(t.getApellido()).thenReturn(apellido);
        lenient().when(t.getJefaturaId()).thenReturn(jefaturaId);
        return t;
    }

    private void conFicha(String nombre, String apellido, String jefaturaId) {
        TrabajadorReferencia t = ficha(nombre, apellido, jefaturaId);
        when(trabajadores.findByIdAndTenantId("trab-1", "t-1")).thenReturn(Optional.of(t));
    }

    private void jefeConUsuario(String jefaturaId) {
        IdentityUsuarioReferencia jefe = mock(IdentityUsuarioReferencia.class);
        when(jefe.getId()).thenReturn("usr-jefe");
        when(users.findByTenantIdAndTrabajadorId("t-1", jefaturaId)).thenReturn(Optional.of(jefe));
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

    @Test
    void atrasoNotificaALaJefaturaDirecta() throws Exception {
        conFicha("Ana", "Pérez", "jefe-1");
        IdentityUsuarioReferencia jefe = mock(IdentityUsuarioReferencia.class);
        when(jefe.getId()).thenReturn("usr-jefe");
        when(users.findByTenantIdAndTrabajadorId("t-1", "jefe-1")).thenReturn(Optional.of(jefe));

        consumer.procesar(atraso("e-atr", "2026-11-03T11:12:40Z", ""));

        Notificacion n = notificacionGuardada();
        assertEquals("usr-jefe", n.getDestinatarioId());
        assertEquals("t-1", n.getTenantId());
        assertEquals("Atraso en marca de entrada", n.getAsunto());
        assertEquals("Ana Pérez marcó entrada a las 08:12 con 12 minutos de atraso (turno 08:00).", n.getCuerpo());
        assertEquals("asistencia.atraso.detectado", n.getTipoEvento());
        assertEquals("m-1", n.getReferenciaId());
        verify(users, never()).findByTenantIdAndRolIn(any(), anyList());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void atrasoSinJefaturaAvisaAAdmins(String jefaturaId) throws Exception {
        conFicha("Ana", "Pérez", jefaturaId);
        unAdminEnElTenant();

        consumer.procesar(atraso("e-atr", "2026-11-03T11:12:40Z", ""));

        assertEquals("adm-1", notificacionGuardada().getDestinatarioId());
        verify(users).findByTenantIdAndRolIn("t-1", ADMINS);
        verify(users, never()).findByTenantIdAndTrabajadorId(any(), any());
    }

    @Test
    void atrasoConJefeSinUsuarioAvisaAAdmins() throws Exception {
        conFicha("Ana", "Pérez", "jefe-1");
        when(users.findByTenantIdAndTrabajadorId("t-1", "jefe-1")).thenReturn(Optional.empty());
        unAdminEnElTenant();

        consumer.procesar(atraso("e-atr", "2026-11-03T11:12:40Z", ""));

        assertEquals("adm-1", notificacionGuardada().getDestinatarioId());
        verify(users).findByTenantIdAndRolIn("t-1", ADMINS);
    }

    @Test
    void atrasoDeTrabajadorNoEncontradoDiceUnTrabajador() throws Exception {
        when(trabajadores.findByIdAndTenantId("trab-1", "t-1")).thenReturn(Optional.empty());
        unAdminEnElTenant();

        consumer.procesar(atraso("e-atr", "2026-11-03T11:12:40Z", ""));

        Notificacion n = notificacionGuardada();
        assertEquals("adm-1", n.getDestinatarioId());
        assertEquals("Un trabajador marcó entrada a las 08:12 con 12 minutos de atraso (turno 08:00).", n.getCuerpo());
        verify(users).findByTenantIdAndRolIn("t-1", ADMINS);
    }

    @Test
    void atrasoConNombreVacioDiceUnTrabajador() throws Exception {
        conFicha("", null, "jefe-1");
        jefeConUsuario("jefe-1");

        consumer.procesar(atraso("e-atr", "2026-11-03T11:12:40Z", ""));

        Notificacion n = notificacionGuardada();
        assertEquals("usr-jefe", n.getDestinatarioId());
        assertTrue(n.getCuerpo().startsWith("Un trabajador marcó"), n.getCuerpo());
    }

    @Test
    void atrasoBuscaSoloEnElTenantDelEvento() throws Exception {
        conFicha("Ana", "Pérez", "jefe-1");
        jefeConUsuario("jefe-1");

        consumer.procesar(atraso("e-atr", "2026-11-03T11:12:40Z", ""));

        verify(trabajadores).findByIdAndTenantId("trab-1", "t-1");
        verify(users).findByTenantIdAndTrabajadorId("t-1", "jefe-1");
        verifyNoMoreInteractions(trabajadores);
    }

    @Test
    void atrasoRepetidoCreaUnaSolaNotificacion() throws Exception {
        when(processed.existsById("e-atr")).thenReturn(false, true);
        conFicha("Ana", "Pérez", "jefe-1");
        jefeConUsuario("jefe-1");
        String mensaje = atraso("e-atr", "2026-11-03T11:12:40Z", "");

        consumer.procesar(mensaje);
        consumer.procesar(mensaje);

        verify(notifications, times(1)).save(any());
        verify(processed, times(1)).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"minutosAtraso\":\"12\"", "\"fechaHora\":\"2026-11-03T11:12:40\"", "\"zona\":\"Marte/Olimpo\"", "\"fechaHora\":null"})
    void atrasoConPayloadInvalidoNoCreaNotificaciones(String reemplazo) {
        String mensaje = atraso("e-atr", "2026-11-03T11:12:40Z", reemplazo);

        assertThrows(RuntimeException.class, () -> consumer.procesar(mensaje));

        verify(notifications, never()).save(any());
        verify(processed, never()).save(any());
        verifyNoInteractions(trabajadores);
    }

    @Test
    void atrasoUsaLaHoraLocalConHorarioDeInvierno() throws Exception {
        conFicha("Ana", "Pérez", "jefe-1");
        jefeConUsuario("jefe-1");

        consumer.procesar(atraso("e-atr", "2026-07-01T12:06:00Z", "\"minutosAtraso\":6"));

        assertEquals("Ana Pérez marcó entrada a las 08:06 con 6 minutos de atraso (turno 08:00).", notificacionGuardada().getCuerpo());
    }

    private SqsClient colaConUnMensaje(String body) {
        SqsClient client = mock(SqsClient.class);
        when(sqs.getIfAvailable()).thenReturn(client);
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body(body).receiptHandle("r-1").build()).build());
        consumer = new SqsEventConsumer(sqs, new ObjectMapper(), notifications, processed, users, trabajadores,
                new TransactionTemplate(transacciones), true, "https://sqs/cola", correo);
        return client;
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumirGuardaAvisosYEventoEnUnaSolaTransaccion() {
        SqsClient client = colaConUnMensaje(evento("Pablo Postulante"));
        unAdminEnElTenant();

        consumer.consumir();

        verify(transacciones).getTransaction(any());
        verify(transacciones).commit(any());
        verify(processed).save(any());
        verify(client).deleteMessage(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumirRevierteLosAvisosSiFallaUnoYNoBorraElMensaje() {
        SqsClient client = colaConUnMensaje(evento("Pablo Postulante"));
        IdentityUsuarioReferencia a1 = mock(IdentityUsuarioReferencia.class);
        IdentityUsuarioReferencia a2 = mock(IdentityUsuarioReferencia.class);
        when(a1.getId()).thenReturn("adm-1");
        when(a2.getId()).thenReturn("adm-2");
        when(users.findByTenantIdAndRolIn(eq("t-1"), anyList())).thenReturn(List.of(a1, a2));
        when(notifications.save(any())).thenReturn(null).thenThrow(new IllegalStateException("base caida"));

        consumer.consumir();

        verify(transacciones).rollback(any());
        verify(transacciones, never()).commit(any());
        verify(processed, never()).save(any());
        verify(client, never()).deleteMessage(any(Consumer.class));
        verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    // ---- plataforma.mensaje.enviado (S7, ADR-020)

    private static String mensaje(String destinatarios) {
        return "{\"eventId\":\"e-msg\",\"eventType\":\"plataforma.mensaje.enviado\",\"tenantId\":\"t-1\",\"aggregateId\":\"msg-1\","
                + "\"payload\":{\"asunto\":\"Aviso\",\"cuerpo\":\"Hola\",\"autorEmail\":\"op@rrhh.local\",\"destinatarios\":" + destinatarios + "}}";
    }

    private void usuario(String id, String tenantId) {
        IdentityUsuarioReferencia u = mock(IdentityUsuarioReferencia.class);
        lenient().when(u.getId()).thenReturn(id);
        lenient().when(u.getTenantId()).thenReturn(tenantId);
        when(users.findById(id)).thenReturn(Optional.of(u));
    }

    @Test
    void mensajeCreaUnaNotificacionPorDestinatarioYEnviaCadaCorreo() {
        usuario("u-1", "t-1");
        usuario("u-2", "t-1");
        consumer.procesarMensaje(mensaje("[{\"usuarioId\":\"u-1\",\"email\":\"a@x.cl\"},{\"usuarioId\":\"u-2\",\"email\":\"b@x.cl\"}]"));
        ArgumentCaptor<Notificacion> n = ArgumentCaptor.forClass(Notificacion.class);
        verify(notifications, times(2)).save(n.capture());
        assertEquals(List.of("u-1", "u-2"), n.getAllValues().stream().map(Notificacion::getDestinatarioId).toList());
        Notificacion primera = n.getAllValues().get(0);
        assertEquals("plataforma.mensaje.enviado", primera.getTipoEvento());
        assertEquals("Aviso", primera.getAsunto());
        assertEquals("Hola", primera.getCuerpo());
        assertEquals("t-1", primera.getTenantId());
        assertEquals("msg-1", primera.getReferenciaId());
        verify(correo).enviar("a@x.cl", "Aviso", "Hola");
        verify(correo).enviar("b@x.cl", "Aviso", "Hola");
        verify(processed).save(any());
    }

    @Test
    void destinatarioDeOtraEmpresaSeOmite() {
        usuario("u-1", "t-1");
        usuario("u-9", "t-2");
        consumer.procesarMensaje(mensaje("[{\"usuarioId\":\"u-1\",\"email\":\"a@x.cl\"},{\"usuarioId\":\"u-9\",\"email\":\"z@x.cl\"}]"));
        verify(notifications, times(1)).save(any());
        verify(correo).enviar("a@x.cl", "Aviso", "Hola");
        verify(correo, never()).enviar(eq("z@x.cl"), any(), any());
    }

    @Test
    void mensajeSinDestinatariosEsIlegible() {
        assertThrows(IllegalArgumentException.class, () -> consumer.procesar(mensaje("[]")));
        verify(processed, never()).save(any());
        verifyNoInteractions(correo);
    }

    @Test
    void mensajeRepetidoNoSeEntregaDosVeces() {
        when(processed.existsById("e-msg")).thenReturn(true);
        consumer.procesarMensaje(mensaje("[{\"usuarioId\":\"u-1\",\"email\":\"a@x.cl\"}]"));
        verify(notifications, never()).save(any());
        verifyNoInteractions(correo);
    }

    @Test
    void unErrorDeSnsNoDeshaceLaNotificacion() {
        usuario("u-1", "t-1");
        doThrow(new RuntimeException("SNS caído")).when(correo).enviar(any(), any(), any());
        consumer.procesarMensaje(mensaje("[{\"usuarioId\":\"u-1\",\"email\":\"a@x.cl\"}]"));
        verify(notifications).save(any());
        verify(processed).save(any());
    }
}
