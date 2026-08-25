package com.microfi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String AUTH_EXCHANGE = "microfi.auth";
    public static final String LOGIN_SUCCESS_QUEUE = "auth.login.success.queue";
    public static final String LOGIN_FAILURE_QUEUE = "auth.login.failure.queue";
    public static final String LOGIN_SUCCESS_KEY = "auth.login.success";
    public static final String LOGIN_FAILURE_KEY = "auth.login.failure";

    // UC-06/07/08 burst protection: a wave of agents reconnecting at once each trigger a
    // reverse-geocoding call to OpenStreetMap's rate-limited free Nominatim service
    // (GeocodingService) — synchronously in the request meant every one of those burst requests
    // held a Core thread open for up to Nominatim's 5s timeout. Collection recording itself (the
    // escrow-ceiling check included) stays fully synchronous — only this non-critical, already
    // best-effort/nullable enrichment moves off the request path.
    public static final String COLLECTION_EXCHANGE = "microfi.collection";
    public static final String COLLECTION_GEOCODE_QUEUE = "collection.geocode.queue";
    public static final String COLLECTION_GEOCODE_KEY = "collection.geocode";

    @Bean
    public TopicExchange authExchange() {
        return new TopicExchange(AUTH_EXCHANGE);
    }

    @Bean
    public Queue loginSuccessQueue() {
        return new Queue(LOGIN_SUCCESS_QUEUE, true);
    }

    @Bean
    public Queue loginFailureQueue() {
        return new Queue(LOGIN_FAILURE_QUEUE, true);
    }

    @Bean
    public Binding loginSuccessBinding(Queue loginSuccessQueue, TopicExchange authExchange) {
        return BindingBuilder.bind(loginSuccessQueue).to(authExchange).with(LOGIN_SUCCESS_KEY);
    }

    @Bean
    public Binding loginFailureBinding(Queue loginFailureQueue, TopicExchange authExchange) {
        return BindingBuilder.bind(loginFailureQueue).to(authExchange).with(LOGIN_FAILURE_KEY);
    }

    @Bean
    public TopicExchange collectionExchange() {
        return new TopicExchange(COLLECTION_EXCHANGE);
    }

    @Bean
    public Queue collectionGeocodeQueue() {
        return new Queue(COLLECTION_GEOCODE_QUEUE, true);
    }

    @Bean
    public Binding collectionGeocodeBinding(Queue collectionGeocodeQueue, TopicExchange collectionExchange) {
        return BindingBuilder.bind(collectionGeocodeQueue).to(collectionExchange).with(COLLECTION_GEOCODE_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
