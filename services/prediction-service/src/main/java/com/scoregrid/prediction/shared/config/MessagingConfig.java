package com.scoregrid.prediction.shared.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    static final String DLX = "scoregrid.dlx";
    static final String Q_MATCH_CACHE = "prediction.match-cache";

    @Bean
    Queue predictionMatchCacheQueue() {
        return QueueBuilder.durable(Q_MATCH_CACHE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(Q_MATCH_CACHE + ".dlq")
                .build();
    }

    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
