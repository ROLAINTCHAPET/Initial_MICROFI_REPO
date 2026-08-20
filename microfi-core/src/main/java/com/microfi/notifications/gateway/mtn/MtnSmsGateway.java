package com.microfi.notifications.gateway.mtn;

import com.microfi.notifications.gateway.AbstractSmsGateway;
import com.microfi.notifications.gateway.SmsSendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MTN Business API (MTN Developer Portal SMS API) — OAuth2 client-credentials, then a REST POST
 * to the SMS outbound endpoint. MTN's exact endpoint path/payload shape varies by market and
 * subscription plan more than Orange's does; {@code mtn.sms.send-path} is intentionally
 * configurable so it can be pointed at whatever MICROFI's actual MTN Business API contract
 * specifies without a code change. Verify request/response field names against that contract
 * before going live.
 */
@Component
public class MtnSmsGateway extends AbstractSmsGateway {

    public static final String VENDOR = "mtn";

    private final String senderAddress;
    private final String sendPath;

    public MtnSmsGateway(WebClient.Builder webClientBuilder,
                          @Value("${mtn.sms.base-url:https://api.mtn.com}") String baseUrl,
                          @Value("${mtn.sms.token-uri:/v1/oauth/access_token}") String tokenUri,
                          @Value("${mtn.sms.send-path:/v1/sms/messages/sms/outbound}") String sendPath,
                          @Value("${mtn.sms.client-id:}") String clientId,
                          @Value("${mtn.sms.client-secret:}") String clientSecret,
                          @Value("${mtn.sms.sender-address:}") String senderAddress) {
        super(webClientBuilder.baseUrl(baseUrl).build(), tokenUri, clientId, clientSecret);
        this.senderAddress = senderAddress;
        this.sendPath = sendPath;
    }

    @Override
    protected String vendorKey() {
        return VENDOR;
    }

    @Override
    public Mono<SmsSendResult> send(String recipientPhone, String message) {
        return accessToken()
                .flatMap(token -> webClient.post()
                        .uri(sendPath)
                        .headers(headers -> headers.setBearerAuth(token))
                        .bodyValue(Map.of(
                                "senderAddress", senderAddress,
                                "receiverAddress", List.of(recipientPhone),
                                "message", message))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> new SmsSendResult(true, String.valueOf(response.get("requestId")), null)))
                .doOnError(e -> log.error("MTN SMS send to {} failed: {}", recipientPhone, e.getMessage()))
                .onErrorResume(e -> Mono.just(new SmsSendResult(false, null, e.getMessage())));
    }
}
