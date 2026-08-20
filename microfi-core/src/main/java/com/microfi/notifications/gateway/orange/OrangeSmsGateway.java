package com.microfi.notifications.gateway.orange;

import com.microfi.notifications.gateway.AbstractSmsGateway;
import com.microfi.notifications.gateway.SmsSendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Orange Developer "SMS API" (api.orange.com/smsmessaging) — OAuth2 client-credentials via
 * {@code /oauth/v3/token}, then {@code POST /smsmessaging/v1/outbound/{senderAddress}/requests}.
 * Verify the exact request/response shape against MICROFI's actual Orange Developer contract
 * before going live — this follows Orange's publicly documented SMS API as of this writing, but
 * account-specific quotas/senderAddress formats can vary by subscription.
 */
@Component
public class OrangeSmsGateway extends AbstractSmsGateway {

    public static final String VENDOR = "orange";

    private final String senderAddress;

    public OrangeSmsGateway(WebClient.Builder webClientBuilder,
                             @Value("${orange.sms.base-url:https://api.orange.com}") String baseUrl,
                             @Value("${orange.sms.token-uri:/oauth/v3/token}") String tokenUri,
                             @Value("${orange.sms.client-id:}") String clientId,
                             @Value("${orange.sms.client-secret:}") String clientSecret,
                             @Value("${orange.sms.sender-address:}") String senderAddress) {
        super(webClientBuilder.baseUrl(baseUrl).build(), tokenUri, clientId, clientSecret);
        this.senderAddress = senderAddress;
    }

    @Override
    protected String vendorKey() {
        return VENDOR;
    }

    @Override
    public Mono<SmsSendResult> send(String recipientPhone, String message) {
        return accessToken()
                .flatMap(token -> webClient.post()
                        .uri("/smsmessaging/v1/outbound/{senderAddress}/requests", "tel:" + senderAddress)
                        .headers(headers -> headers.setBearerAuth(token))
                        .bodyValue(Map.of("outboundSMSMessageRequest", Map.of(
                                "address", "tel:" + recipientPhone,
                                "senderAddress", "tel:" + senderAddress,
                                "outboundSMSTextMessage", Map.of("message", message))))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> new SmsSendResult(true, String.valueOf(response.get("resourceURL")), null)))
                .doOnError(e -> log.error("Orange SMS send to {} failed: {}", recipientPhone, e.getMessage()))
                .onErrorResume(e -> Mono.just(new SmsSendResult(false, null, e.getMessage())));
    }
}
