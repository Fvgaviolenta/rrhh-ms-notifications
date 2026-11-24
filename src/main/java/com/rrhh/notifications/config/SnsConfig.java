package com.rrhh.notifications.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

/** Cliente SNS solo cuando hay tema de correos configurado (SNS_CORREOS_TOPIC_ARN); credenciales del rol de la tarea. */
@Configuration
public class SnsConfig {
    @Bean
    @ConditionalOnExpression("!'${rrhh.sns.correos-topic-arn:}'.isBlank()")
    SnsClient snsClient(@Value("${rrhh.sns.region:us-east-1}") String region) {
        return SnsClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }
}
