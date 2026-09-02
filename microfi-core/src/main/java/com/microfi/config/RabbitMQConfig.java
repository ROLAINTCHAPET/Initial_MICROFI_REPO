package com.microfi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
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
    // held a Core thread open for up to Nominatim's 5s timeout. This non-critical, already
    // best-effort/nullable enrichment moves off the request path entirely (fire-and-forget).
    public static final String COLLECTION_EXCHANGE = "microfi.collection";
    public static final String COLLECTION_GEOCODE_QUEUE = "collection.geocode.queue";
    public static final String COLLECTION_GEOCODE_KEY = "collection.geocode";

    // UC-14: same fire-and-forget reverse-geocoding as collections, applied to an SOS alert's
    // lat/lon — resolving a place name is a display nicety for the Back-Office SOS console, never
    // something an emergency trigger should wait on. Shares the collection exchange rather than
    // standing up a new one; only the routing key differs.
    public static final String SOS_GEOCODE_QUEUE = "sos.geocode.queue";
    public static final String SOS_GEOCODE_KEY = "sos.geocode";

    // The actual burst-protection this system was designed around: many agents reconnecting
    // together each submit a batch of offline collections at once. Recording a collection can't
    // be fire-and-forget the way geocoding is — the caller needs a real pass/fail per item
    // (duplicate/rejected/succeeded) to manage its own offline queue, and the escrow-ceiling
    // check has to run before any answer is given. So this uses RabbitMQ's request-reply
    // pattern (RabbitTemplate#convertSendAndReceive, direct reply-to — no manual reply queue) —
    // the HTTP caller still gets a synchronous per-item answer, but the actual processing is
    // funneled through collectionRecordContainerFactory's *bounded* consumer pool below, so a
    // burst durably queues in the broker instead of every concurrent request racing directly for
    // Core's thread pool and Postgres connections the way it does today. See
    // CollectionRecordDispatcher/CollectionRecordListener.
    public static final String COLLECTION_RECORD_QUEUE = "collection.record.queue";
    public static final String COLLECTION_RECORD_KEY = "collection.record";

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
    public Queue sosGeocodeQueue() {
        return new Queue(SOS_GEOCODE_QUEUE, true);
    }

    @Bean
    public Binding sosGeocodeBinding(Queue sosGeocodeQueue, TopicExchange collectionExchange) {
        return BindingBuilder.bind(sosGeocodeQueue).to(collectionExchange).with(SOS_GEOCODE_KEY);
    }

    @Bean
    public Queue collectionRecordQueue() {
        return new Queue(COLLECTION_RECORD_QUEUE, true);
    }

    @Bean
    public Binding collectionRecordBinding(Queue collectionRecordQueue, TopicExchange collectionExchange) {
        return BindingBuilder.bind(collectionRecordQueue).to(collectionExchange).with(COLLECTION_RECORD_KEY);
    }

    /**
     * The actual throttle: no matter how many agents hit /collections or /collections/sync at
     * once, only this many collection-record messages are processed at the same time — the rest
     * durably wait in {@link #COLLECTION_RECORD_QUEUE} instead of every request racing directly
     * for Core's thread pool and the Postgres connection pool. Deliberately separate from the
     * default listener container (which the geocode/auth listeners use unbounded) since this is
     * the one queue where the concurrency limit is the entire point.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory collectionRecordContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jsonMessageConverter,
            @Value("${collection.record.consumer.concurrency:5}") int concurrency,
            @Value("${collection.record.consumer.max-concurrency:20}") int maxConcurrency) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        return factory;
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
