package com.rrhh.notifications.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicResponse;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Correo de la plataforma por Amazon SNS (S7, ADR-020). Cada correo tiene una suscripción email al tema con filtro
 * por el atributo {@code destinatario}; se crea la primera vez y la persona debe confirmarla desde el correo de AWS.
 */
@Service
public class CorreoSnsPublisher {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROTOCOLO = "email";
    private static final String ASUNTO_POR_DEFECTO = "Mensaje de la plataforma RRHH";

    private final ObjectProvider<SnsClient> sns;
    private final String topicArn;

    public CorreoSnsPublisher(ObjectProvider<SnsClient> sns, @Value("${rrhh.sns.correos-topic-arn:}") String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn == null ? "" : topicArn.trim();
    }

    /** Sin tema o sin cliente no hace nada: la notificación en la app es el registro del mensaje. */
    public void enviar(String correo, String asunto, String cuerpo) {
        SnsClient client = sns.getIfAvailable();
        if (client == null || topicArn.isBlank() || correo == null || correo.isBlank()) {
            return;
        }
        // Siempre en minúsculas: la suscripción, su filtro y el atributo del mensaje deben coincidir exactamente.
        String email = correo.trim().toLowerCase(Locale.ROOT);
        if (!suscrito(client, email)) {
            client.subscribe(SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol(PROTOCOLO)
                    .endpoint(email)
                    .attributes(Map.of("FilterPolicy", filtro(email)))
                    .returnSubscriptionArn(true)
                    .build());
        }
        client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .subject(subject(asunto))
                .message(asunto + "\n\n" + cuerpo)
                .messageAttributes(Map.of("destinatario",
                        MessageAttributeValue.builder().dataType("String").stringValue(email).build()))
                .build());
    }

    private boolean suscrito(SnsClient client, String email) {
        String token = null;
        do {
            ListSubscriptionsByTopicResponse pagina = client.listSubscriptionsByTopic(
                    ListSubscriptionsByTopicRequest.builder().topicArn(topicArn).nextToken(token).build());
            boolean encontrado = pagina.subscriptions().stream()
                    .anyMatch(s -> PROTOCOLO.equals(s.protocol()) && email.equalsIgnoreCase(s.endpoint()));
            if (encontrado) {
                return true;
            }
            token = pagina.nextToken();
        } while (token != null && !token.isBlank());
        return false;
    }

    private static String filtro(String email) {
        try {
            return JSON.writeValueAsString(Map.of("destinatario", List.of(email)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible armar el filtro de la suscripción", e);
        }
    }

    /** SNS acepta en Subject solo ASCII imprimible, sin saltos de línea y de menos de 100 caracteres. */
    static String subject(String asunto) {
        String sinMarcas = Normalizer.normalize(asunto == null ? "" : asunto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String ascii = sinMarcas.replaceAll("[^\\x20-\\x7E]", "").trim();
        if (ascii.length() > 99) {
            ascii = ascii.substring(0, 99).trim();
        }
        return ascii.isEmpty() ? ASUNTO_POR_DEFECTO : ascii;
    }
}
