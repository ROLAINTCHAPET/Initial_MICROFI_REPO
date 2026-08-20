package com.microfi.cbsclient;

import com.microfi.shared.dto.MiddlewareCollectionLine;
import com.microfi.shared.dto.MiddlewareExportAck;
import com.microfi.shared.dto.MiddlewareTransactionPostResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CbsClientServiceTest {

    @Test
    void submitDailyExportParsesAcknowledgement() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"acknowledged\":true,\"ackReference\":\"EXPACK-123\"}")
                        .build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        CbsClientService service = new CbsClientService(builder, "http://middleware.invalid");

        MiddlewareExportAck ack = service.submitDailyExport(UUID.randomUUID(), "export/x.csv", "CSV").block();

        assertThat(ack).isNotNull();
        assertThat(ack.isAcknowledged()).isTrue();
        assertThat(ack.getAckReference()).isEqualTo("EXPACK-123");
    }

    @Test
    void submitDailyExportPropagatesErrorOnFailure() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        CbsClientService service = new CbsClientService(builder, "http://middleware.invalid");

        StepVerifier.create(service.submitDailyExport(UUID.randomUUID(), "export/x.csv", "CSV"))
                .expectError()
                .verify();
    }

    @Test
    void postTransactionsParsesResult() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"success\":true,\"postedReferences\":[\"CBSTX-1\"]}")
                        .build()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        CbsClientService service = new CbsClientService(builder, "http://middleware.invalid");

        MiddlewareCollectionLine line = MiddlewareCollectionLine.builder()
                .collectionId(UUID.randomUUID()).memberId("CBS-1").amountXaf(1000L).collectedAt(Instant.now()).build();
        MiddlewareTransactionPostResult result = service.postTransactions(List.of(line), "idem-1").block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPostedReferences()).containsExactly("CBSTX-1");
    }
}
