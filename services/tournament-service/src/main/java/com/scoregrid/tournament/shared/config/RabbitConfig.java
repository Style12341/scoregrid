package com.scoregrid.tournament.shared.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the whole ScoreGrid messaging topology.
 *
 * <p>Every queue, binding and DLQ is declared here — in the publisher — rather
 * than in each consumer. Reason: on a cold start a consumer can boot before the
 * exchange exists, and a binding to a missing exchange fails. Declaring the
 * full topology in one place means it exists after the first boot of any
 * service, and declarations are idempotent.
 *
 * <p>Contract: docs/contracts.md#events--rabbitmq. Do not add a queue here
 * without adding it to that document first.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "scoregrid.events";
    public static final String DLX = "scoregrid.dlx";

    public static final String Q_PREDICTION_MATCH_CACHE = "prediction.match-cache";
    public static final String Q_SCORE_MATCH_FINISHED = "score.match-finished";

    private static final String DLQ_SUFFIX = ".dlq";

    // ── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    // ── prediction.match-cache: keeps the kickoff lock answerable offline ────

    @Bean
    Queue predictionMatchCacheQueue() {
        return durableWithDlq(Q_PREDICTION_MATCH_CACHE);
    }

    @Bean
    Binding predictionMatchScheduledBinding() {
        return BindingBuilder.bind(predictionMatchCacheQueue()).to(eventsExchange()).with("match.scheduled");
    }

    @Bean
    Binding predictionMatchUpdatedBinding() {
        return BindingBuilder.bind(predictionMatchCacheQueue()).to(eventsExchange()).with("match.updated");
    }

    @Bean
    Queue predictionMatchCacheDlq() {
        return new Queue(Q_PREDICTION_MATCH_CACHE + DLQ_SUFFIX, true);
    }

    @Bean
    Binding predictionMatchCacheDlqBinding() {
        return BindingBuilder.bind(predictionMatchCacheDlq()).to(deadLetterExchange())
                .with(Q_PREDICTION_MATCH_CACHE + DLQ_SUFFIX);
    }

    // ── score.match-finished: triggers scoring ───────────────────────────────

    @Bean
    Queue scoreMatchFinishedQueue() {
        return durableWithDlq(Q_SCORE_MATCH_FINISHED);
    }

    @Bean
    Binding scoreMatchFinishedBinding() {
        return BindingBuilder.bind(scoreMatchFinishedQueue()).to(eventsExchange()).with("match.finished");
    }

    @Bean
    Queue scoreMatchFinishedDlq() {
        return new Queue(Q_SCORE_MATCH_FINISHED + DLQ_SUFFIX, true);
    }

    @Bean
    Binding scoreMatchFinishedDlqBinding() {
        return BindingBuilder.bind(scoreMatchFinishedDlq()).to(deadLetterExchange())
                .with(Q_SCORE_MATCH_FINISHED + DLQ_SUFFIX);
    }

    // ── Serialization ────────────────────────────────────────────────────────

    /**
     * Boot 4 ships Jackson 3, so this is JacksonJsonMessageConverter — the
     * Jackson2-prefixed class is the legacy one and needs Jackson 2 on the
     * classpath.
     */
    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    private Queue durableWithDlq(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(name + DLQ_SUFFIX)
                .build();
    }
}
