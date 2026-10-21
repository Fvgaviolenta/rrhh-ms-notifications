package com.rrhh.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.notifications.model.IdentityUsuarioReferencia;
import com.rrhh.notifications.model.Notificacion;
import com.rrhh.notifications.model.ProcessedEvent;
import com.rrhh.notifications.repository.IdentityUsuarioReferenciaRepository;
import com.rrhh.notifications.repository.NotificacionRepository;
import com.rrhh.notifications.repository.ProcessedEventRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SqsEventConsumer {
    private final ObjectProvider<SqsClient> sqs;
    private final ObjectMapper json;
    private final NotificacionRepository notifications;
    private final ProcessedEventRepository processed;
    private final IdentityUsuarioReferenciaRepository users;
    private final boolean enabled;
    private final String queueUrl;

    public SqsEventConsumer(ObjectProvider<SqsClient> sqs, ObjectMapper json, NotificacionRepository notifications,
                            ProcessedEventRepository processed, IdentityUsuarioReferenciaRepository users,
                            @Value("${rrhh.sqs.enabled:false}") boolean enabled,
                            @Value("${rrhh.sqs.events-queue-url:}") String queueUrl) {
        this.sqs=sqs; this.json=json; this.notifications=notifications; this.processed=processed; this.users=users; this.enabled=enabled; this.queueUrl=queueUrl;
    }

    @Scheduled(fixedDelayString = "${rrhh.sqs.poll-delay-ms:5000}")
    public void consumir() {
        SqsClient client=sqs.getIfAvailable(); if(!enabled || queueUrl.isBlank() || client==null) return;
        List<Message> messages=client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(20).visibilityTimeout(60).build()).messages();
        for(Message message:messages) {
            try { procesar(message.body()); client.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle())); }
            catch (Exception ignored) { /* SQS volverá a entregar el mensaje tras el visibility timeout. */ }
        }
    }

    @Transactional
    void procesar(String body) throws Exception {
        JsonNode event=json.readTree(body); String id=required(event,"eventId");
        if(processed.existsById(id)) return;
        String tenant=required(event,"tenantId"), type=required(event,"eventType"); JsonNode payload=event.path("payload");
        String referencia=event.hasNonNull("aggregateId") && !event.get("aggregateId").asText().isBlank() ? event.get("aggregateId").asText() : null;
        List<IdentityUsuarioReferencia> recipients = switch(type) {
            case "ausencia.solicitada" -> users.findByTenantIdAndRolIn(tenant, List.of("ADMIN_RRHH", "Admin de RRHH", "SUPERADMIN", "SuperAdmin"));
            case "ausencia.aprobada", "ausencia.rechazada", "contrato.creado", "contrato.finalizado" -> users.findByTenantIdAndTrabajadorId(tenant, required(payload,"trabajadorId")).stream().toList();
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + type);
        };
        for(IdentityUsuarioReferencia user:recipients) crear(tenant,user.getId(),type,referencia,payload);
        ProcessedEvent done=new ProcessedEvent(); done.setEventId(id); done.setProcesadoEn(Instant.now()); processed.save(done);
    }

    private void crear(String tenant,String userId,String type,String referencia,JsonNode payload) {
        String asunto=switch(type){case "ausencia.solicitada"->"Nueva solicitud de ausencia";case "ausencia.aprobada"->"Solicitud de ausencia aprobada";case "ausencia.rechazada"->"Solicitud de ausencia rechazada";case "contrato.creado"->"Contrato registrado";case "contrato.finalizado"->"Contrato finalizado";default->"Actualización";};
        String cuerpo=switch(type){case "ausencia.solicitada"->"Existe una solicitud de ausencia pendiente de revisión.";case "ausencia.aprobada"->"Tu solicitud de ausencia fue aprobada.";case "ausencia.rechazada"->"Tu solicitud de ausencia fue rechazada.";case "contrato.creado"->"Se registró tu contrato.";case "contrato.finalizado"->"Tu contrato fue finalizado.";default->"";};
        if("ausencia.rechazada".equals(type) && payload.hasNonNull("motivoRechazo")) cuerpo += " Motivo: " + payload.get("motivoRechazo").asText();
        Notificacion n=new Notificacion(); n.setId(UUID.randomUUID().toString()); n.setTenantId(tenant); n.setDestinatarioId(userId); n.setCanal("IN_APP"); n.setAsunto(asunto); n.setCuerpo(cuerpo); n.setEstado("PENDIENTE"); n.setCreadoEn(Instant.now()); n.setTipoEvento(type); n.setReferenciaId(referencia); notifications.save(n);
    }
    private String required(JsonNode node,String field){if(!node.hasNonNull(field)||node.get(field).asText().isBlank())throw new IllegalArgumentException("Evento sin "+field);return node.get(field).asText();}
}
