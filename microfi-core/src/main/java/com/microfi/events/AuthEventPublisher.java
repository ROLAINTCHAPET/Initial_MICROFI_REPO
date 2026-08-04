package com.microfi.events;

import com.microfi.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishSuccess(String employeeCode, String imei) {
        AuthEvent event = AuthEvent.success(employeeCode, imei);
        log.info("Publishing LOGIN_SUCCESS event for agent: {}", employeeCode);
        publish(RabbitMQConfig.LOGIN_SUCCESS_KEY, event);
    }

    public void publishFailure(String employeeCode, String imei) {
        AuthEvent event = AuthEvent.failure(employeeCode, imei);
        log.warn("Publishing LOGIN_FAILURE event for agent: {}", employeeCode);
        publish(RabbitMQConfig.LOGIN_FAILURE_KEY, event);
    }

    // Auth events are best-effort audit signal, not part of the auth decision: a broker outage
    // must never block or fail a login (mobile requests are never blocked on downstream systems).
    private void publish(String routingKey, AuthEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.AUTH_EXCHANGE, routingKey, event);
        } catch (Exception e) {
            log.error("Failed to publish auth event {} for agent {}: {}", event.eventType(), event.employeeCode(), e.getMessage());
        }
    }
}
