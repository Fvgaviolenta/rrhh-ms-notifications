package com.rrhh.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.notifications.model.IdentityUsuarioReferencia;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.model.ProcessedEvent;
import com.rrhh.notifications.model.TrabajadorReferencia;
import com.rrhh.notifications.repository.IdentityUsuarioReferenciaRepository;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.repository.ProcessedEventRepository;
import com.rrhh.notifications.repository.TrabajadorReferenciaRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SqsEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(SqsEventConsumer.class);
    private static final String ATRASO = "asistencia.atraso.detectado";
    private static final String MENSAJE = "plataforma.mensaje.enviado";
    private static final List<String> ADMINS = List.of("ADMIN_RRHH", "Admin de RRHH", "SUPERADMIN", "SuperAdmin");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private final ObjectProvider<SqsClient> sqs;
    private final ObjectMapper json;
    private final NotificacionRepository notifications;
    private final ProcessedEventRepository processed;
    private final IdentityUsuarioReferenciaRepository users;
    private final TrabajadorReferenciaRepository trabajadores;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final String queueUrl;
    private final CorreoSnsPublisher correo;

    /** Correo pendiente de un mensaje de la plataforma: se envía después de confirmar la transacción. */
    record CorreoPendiente(String email, String asunto, String cuerpo) {}

    public SqsEventConsumer(ObjectProvider<SqsClient> sqs, ObjectMapper json, NotificacionRepository notifications,
                            ProcessedEventRepository processed, IdentityUsuarioReferenciaRepository users,
                            TrabajadorReferenciaRepository trabajadores, TransactionTemplate tx,
                            @Value("${rrhh.sqs.enabled:false}") boolean enabled,
                            @Value("${rrhh.sqs.events-queue-url:}") String queueUrl,
                            CorreoSnsPublisher correo) {
        this.correo=correo;
        this.sqs=sqs; this.json=json; this.notifications=notifications; this.processed=processed; this.users=users; this.trabajadores=trabajadores; this.tx=tx; this.enabled=enabled; this.queueUrl=queueUrl;
    }

    @Scheduled(fixedDelayString = "${rrhh.sqs.poll-delay-ms:5000}")
    public void consumir() {
        SqsClient client=sqs.getIfAvailable(); if(!enabled || queueUrl.isBlank() || client==null) return;
        List<Message> messages=client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(20).visibilityTimeout(60).build()).messages();
        for(Message message:messages) {
            try { procesarMensaje(message.body()); client.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle())); }
            catch (Exception ignored) { /* SQS volverá a entregar el mensaje tras el visibility timeout. */ }
        }
    }

    /**
     * Los avisos y el processed_event se confirman juntos (S7): una llamada a procesar desde esta misma clase
     * no pasa por el proxy de @Transactional, por eso la transaccion se abre aqui de forma explicita.
     */
    private List<CorreoPendiente> procesarEnTransaccion(String body) {
        return tx.execute(status -> {
            try { return procesar(body); }
            catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new IllegalArgumentException("Mensaje ilegible", e); }
        });
    }

    /**
     * Procesa un mensaje de SQS y, ya confirmada la transacción, envía los correos de la plataforma (S7). El correo
     * es de mejor esfuerzo: un error de SNS se registra y no deshace la notificación en la app, que es el registro.
     */
    void procesarMensaje(String body) {
        List<CorreoPendiente> correos = procesarEnTransaccion(body);
        for (CorreoPendiente c : correos == null ? List.<CorreoPendiente>of() : correos) {
            try { correo.enviar(c.email(), c.asunto(), c.cuerpo()); }
            catch (RuntimeException e) { log.warn("No se pudo enviar el correo de la plataforma: {}", e.getMessage()); }
        }
    }

    List<CorreoPendiente> procesar(String body) throws Exception {
        JsonNode event=json.readTree(body); String id=required(event,"eventId");
        if(processed.existsById(id)) return List.of();
        List<CorreoPendiente> correos=new ArrayList<>();
        String tenant=required(event,"tenantId"), type=required(event,"eventType"); JsonNode payload=event.path("payload");
        String referencia=event.hasNonNull("aggregateId") && !event.get("aggregateId").asText().isBlank() ? event.get("aggregateId").asText() : null;
        if(ATRASO.equals(type)) avisarAtraso(tenant,referencia,payload);
        else if(MENSAJE.equals(type)) correos.addAll(entregarMensaje(tenant,referencia,payload));
        else {
            List<IdentityUsuarioReferencia> recipients = switch(type) {
                case "ausencia.solicitada", "acceso.solicitado" -> users.findByTenantIdAndRolIn(tenant, ADMINS);
                case "ausencia.aprobada", "ausencia.rechazada", "contrato.creado", "contrato.finalizado" -> users.findByTenantIdAndTrabajadorId(tenant, required(payload,"trabajadorId")).stream().toList();
                default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + type);
            };
            for(IdentityUsuarioReferencia user:recipients) crear(tenant,user.getId(),type,referencia,payload);
        }
        ProcessedEvent done=new ProcessedEvent(); done.setEventId(id); done.setProcesadoEn(Instant.now()); processed.save(done);
        return correos;
    }

    /**
     * Mensaje de la plataforma (S7): una notificación IN_APP por destinatario que sea usuario de la empresa del evento
     * (los demás se omiten) y el correo pendiente para cada uno.
     */
    private List<CorreoPendiente> entregarMensaje(String tenant,String referencia,JsonNode payload) {
        String asunto=required(payload,"asunto"), cuerpo=required(payload,"cuerpo");
        JsonNode destinatarios=payload.path("destinatarios");
        if(!destinatarios.isArray() || destinatarios.isEmpty()) throw new IllegalArgumentException("Evento sin destinatarios");
        List<CorreoPendiente> correos=new ArrayList<>();
        for(JsonNode d:destinatarios) {
            String usuarioId=required(d,"usuarioId"), email=required(d,"email");
            boolean deLaEmpresa=users.findById(usuarioId).map(u->tenant.equals(u.getTenantId())).orElse(false);
            if(!deLaEmpresa) { log.warn("Destinatario {} fuera de la empresa {}: se omite", usuarioId, tenant); continue; }
            guardar(tenant,usuarioId,MENSAJE,referencia,asunto,cuerpo);
            correos.add(new CorreoPendiente(email,asunto,cuerpo));
        }
        return correos;
    }

    private void crear(String tenant,String userId,String type,String referencia,JsonNode payload) {
        String asunto=switch(type){case "ausencia.solicitada"->"Nueva solicitud de ausencia";case "acceso.solicitado"->"Nueva solicitud de acceso";case "ausencia.aprobada"->"Solicitud de ausencia aprobada";case "ausencia.rechazada"->"Solicitud de ausencia rechazada";case "contrato.creado"->"Contrato registrado";case "contrato.finalizado"->"Contrato finalizado";default->"Actualización";};
        String cuerpo=switch(type){case "ausencia.solicitada"->"Existe una solicitud de ausencia pendiente de revisión.";case "acceso.solicitado"->solicitante(payload)+" solicitó acceso a la empresa.";case "ausencia.aprobada"->"Tu solicitud de ausencia fue aprobada.";case "ausencia.rechazada"->"Tu solicitud de ausencia fue rechazada.";case "contrato.creado"->"Se registró tu contrato.";case "contrato.finalizado"->"Tu contrato fue finalizado.";default->"";};
        if("ausencia.rechazada".equals(type) && payload.hasNonNull("motivoRechazo")) cuerpo += " Motivo: " + payload.get("motivoRechazo").asText();
        guardar(tenant,userId,type,referencia,asunto,cuerpo);
    }

    /**
     * Aviso de atraso (S5, S6): valida el payload completo antes de leer datos, busca la ficha solo en el
     * tenant del evento y avisa a la jefatura directa o, si no tiene usuario, a Admin de RRHH del tenant.
     */
    private void avisarAtraso(String tenant,String referencia,JsonNode payload) {
        String trabajadorId=required(payload,"trabajadorId"), fechaHora=required(payload,"fechaHora"), turno=required(payload,"turnoHoraInicio"), zona=required(payload,"zona");
        if(!payload.path("minutosAtraso").isInt()) throw new IllegalArgumentException("Evento sin minutosAtraso entero");
        String hora=HORA.format(Instant.parse(fechaHora).atZone(ZoneId.of(zona)));
        Optional<TrabajadorReferencia> ficha=trabajadores.findByIdAndTenantId(trabajadorId,tenant);
        String jefaturaId=ficha.map(TrabajadorReferencia::getJefaturaId).filter(j->!j.isBlank()).orElse(null);
        List<IdentityUsuarioReferencia> recipients=jefaturaId==null ? List.of() : users.findByTenantIdAndTrabajadorId(tenant,jefaturaId).stream().toList();
        if(recipients.isEmpty()) recipients=users.findByTenantIdAndRolIn(tenant,ADMINS);
        String cuerpo=nombre(ficha)+" marcó entrada a las "+hora+" con "+payload.get("minutosAtraso").asInt()+" minutos de atraso (turno "+turno+").";
        for(IdentityUsuarioReferencia user:recipients) guardar(tenant,user.getId(),ATRASO,referencia,"Atraso en marca de entrada",cuerpo);
    }

    private static String nombre(Optional<TrabajadorReferencia> ficha) {
        return ficha.filter(t->noVacio(t.getNombre()) && noVacio(t.getApellido())).map(t->(t.getNombre()+" "+t.getApellido()).trim()).orElse("Un trabajador");
    }
    private static boolean noVacio(String s){return s!=null && !s.isBlank();}

    private void guardar(String tenant,String userId,String type,String referencia,String asunto,String cuerpo) {
        Notificacion n=new Notificacion(); n.setId(UUID.randomUUID().toString()); n.setTenantId(tenant); n.setDestinatarioId(userId); n.setCanal("IN_APP"); n.setAsunto(asunto); n.setCuerpo(cuerpo); n.setEstado("PENDIENTE"); n.setCreadoEn(Instant.now()); n.setTipoEvento(type); n.setReferenciaId(referencia); notifications.save(n);
    }
    private String solicitante(JsonNode payload){return payload.hasNonNull("nombre")&&!payload.get("nombre").asText().isBlank()?payload.get("nombre").asText():required(payload,"email");}
    private String required(JsonNode node,String field){if(!node.hasNonNull(field)||node.get(field).asText().isBlank())throw new IllegalArgumentException("Evento sin "+field);return node.get(field).asText();}
}
