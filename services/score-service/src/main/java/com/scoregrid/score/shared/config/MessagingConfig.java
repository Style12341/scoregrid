package com.scoregrid.score.shared.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumer-side messaging setup.
 *
 * <p>The topology (exchanges, queues, bindings, DLQs) is declared once in
 * tournament-service — see docs/contracts.md#events--rabbitmq. This service only
 * needs the matching JSON converter so @RabbitListener can deserialize payloads.
 *
 * <p>Boot 4 ships Jackson 3, so this is JacksonJsonMessageConverter; the
 * Jackson2-prefixed class is the legacy one and needs Jackson 2 on the classpath.
 */
@Configuration
public class MessagingConfig {

    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
