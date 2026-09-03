package com.microfi.transactions.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeocodingServiceTest {

    // Zero backoff in every test — real retry delays would otherwise make the suite pay Nominatim's
    // own wall-clock cost for cases that are only exercising the retry logic itself.
    private static GeocodingService service(WebClient.Builder builder, int maxAttempts) {
        return new GeocodingService(builder, "http://nominatim.invalid", maxAttempts, 0);
    }

    @Test
    void reverseGeocodeParsesDisplayName() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"display_name\":\"Akwa, Douala, Cameroon\"}")
                        .build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        assertThat(service(builder, 3).reverseGeocode(4.05, 9.70)).isEqualTo("Akwa, Douala, Cameroon");
    }

    @Test
    void reverseGeocodeReturnsNullOnFailureInsteadOfThrowing() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        assertThat(service(builder, 3).reverseGeocode(4.05, 9.70)).isNull();
    }

    @Test
    void reverseGeocodeRetriesAfterATransientFailureAndSucceeds() {
        AtomicInteger callCount = new AtomicInteger();
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenAnswer(invocation -> callCount.getAndIncrement() == 0
                ? Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
                : Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"display_name\":\"Akwa, Douala, Cameroon\"}")
                        .build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        assertThat(service(builder, 3).reverseGeocode(4.05, 9.70)).isEqualTo("Akwa, Douala, Cameroon");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void reverseGeocodeGivesUpAfterConfiguredAttemptsAndReturnsNull() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        assertThat(service(builder, 3).reverseGeocode(4.05, 9.70)).isNull();
        verify(exchangeFunction, times(3)).exchange(any());
    }
}
