package com.microfi.notifications.gateway.orange;

import com.microfi.notifications.gateway.SmsSendResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrangeSmsGatewayTest {

    @Test
    void sendFetchesTokenThenPostsAndReturnsSuccess() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"access_token\":\"tok-123\",\"expires_in\":3600}")
                        .build()))
                .thenReturn(Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"resourceURL\":\"https://api.orange.com/smsmessaging/v1/outbound/tel:%2B237600/requests/1\"}")
                        .build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        OrangeSmsGateway gateway = new OrangeSmsGateway(builder, "https://api.orange.invalid", "/oauth/v3/token",
                "client-id", "client-secret", "237600000000");

        SmsSendResult result = gateway.send("237611111111", "Test message").block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.providerReference()).isEqualTo("https://api.orange.com/smsmessaging/v1/outbound/tel:%2B237600/requests/1");
    }

    @Test
    void sendReturnsFailureResultOnGatewayError() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        OrangeSmsGateway gateway = new OrangeSmsGateway(builder, "https://api.orange.invalid", "/oauth/v3/token",
                "client-id", "client-secret", "237600000000");

        SmsSendResult result = gateway.send("237611111111", "Test message").block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.providerReference()).isNull();
    }

    @Test
    void vendorKeyIsOrange() {
        OrangeSmsGateway gateway = new OrangeSmsGateway(WebClient.builder(), "https://api.orange.invalid",
                "/oauth/v3/token", "id", "secret", "237600000000");
        assertThat(gateway.vendor()).isEqualTo("orange");
    }
}
