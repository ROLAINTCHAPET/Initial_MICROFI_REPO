package com.microfi.transactions.service;

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

class GeocodingServiceTest {

    @Test
    void reverseGeocodeParsesDisplayName() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"display_name\":\"Akwa, Douala, Cameroon\"}")
                        .build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        GeocodingService service = new GeocodingService(builder, "http://nominatim.invalid");

        assertThat(service.reverseGeocode(4.05, 9.70)).isEqualTo("Akwa, Douala, Cameroon");
    }

    @Test
    void reverseGeocodeReturnsNullOnFailureInsteadOfThrowing() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        GeocodingService service = new GeocodingService(builder, "http://nominatim.invalid");

        assertThat(service.reverseGeocode(4.05, 9.70)).isNull();
    }
}
