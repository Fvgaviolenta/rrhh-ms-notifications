package com.rrhh.notifications.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicResponse;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.Subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CorreoSnsPublisherTest {

    private static final String TEMA = "arn:aws:sns:us-east-1:1:rrhh-correos";

    private final SnsClient sns = mock(SnsClient.class);

    @SuppressWarnings("unchecked")
    private CorreoSnsPublisher publisher(String tema) {
        ObjectProvider<SnsClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sns);
        return new CorreoSnsPublisher(provider, tema);
    }

    private void suscripciones(Subscription... existentes) {
        when(sns.listSubscriptionsByTopic(any(ListSubscriptionsByTopicRequest.class)))
                .thenReturn(ListSubscriptionsByTopicResponse.builder().subscriptions(existentes).build());
    }

    @Test
    void suscribeElCorreoLaPrimeraVezConFiltroPorDestinatario() {
        suscripciones();
        publisher(TEMA).enviar("a@x.cl", "Aviso", "Hola");
        ArgumentCaptor<SubscribeRequest> sub = ArgumentCaptor.forClass(SubscribeRequest.class);
        verify(sns).subscribe(sub.capture());
        assertEquals("email", sub.getValue().protocol());
        assertEquals("a@x.cl", sub.getValue().endpoint());
        assertEquals(TEMA, sub.getValue().topicArn());
        assertEquals("{\"destinatario\":[\"a@x.cl\"]}", sub.getValue().attributes().get("FilterPolicy"));
    }

    @Test
    void noVuelveASuscribirUnCorreoYaSuscrito() {
        suscripciones(Subscription.builder().protocol("email").endpoint("A@x.cl").build());
        publisher(TEMA).enviar("a@x.cl", "Aviso", "Hola");
        verify(sns, never()).subscribe(any(SubscribeRequest.class));
    }

    @Test
    void publicaConElAtributoDestinatario() {
        suscripciones(Subscription.builder().protocol("email").endpoint("a@x.cl").build());
        publisher(TEMA).enviar("a@x.cl", "Aviso", "Hola");
        ArgumentCaptor<PublishRequest> pub = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(pub.capture());
        assertEquals(TEMA, pub.getValue().topicArn());
        assertEquals("a@x.cl", pub.getValue().messageAttributes().get("destinatario").stringValue());
        assertEquals("String", pub.getValue().messageAttributes().get("destinatario").dataType());
        assertTrue(pub.getValue().message().startsWith("Aviso\n\nHola"));
    }

    @Test
    void asuntoConTildesSeTransliteraEnElSubject() {
        suscripciones(Subscription.builder().protocol("email").endpoint("a@x.cl").build());
        publisher(TEMA).enviar("a@x.cl", "Revisión de contratación", "Hola");
        ArgumentCaptor<PublishRequest> pub = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(pub.capture());
        assertEquals("Revision de contratacion", pub.getValue().subject());
        assertTrue(pub.getValue().message().startsWith("Revisión de contratación\n\n"));
    }

    @Test
    void sinTemaConfiguradoNoLlamaASns() {
        publisher("").enviar("a@x.cl", "Aviso", "Hola");
        verifyNoInteractions(sns);
    }
}
