package com.microfi.notifications.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the active {@link SmsGateway} for the configured carrier. New carriers register
 * themselves as Spring beans and become selectable purely via the {@code sms.vendor} property,
 * without touching call sites — mirrors {@code CbsAdapterFactory} in the middleware.
 */
@Component
@Slf4j
public class SmsGatewayFactory {

    private final Map<String, SmsGateway> gatewaysByVendor;
    private final String activeVendor;

    public SmsGatewayFactory(List<SmsGateway> gateways, @Value("${sms.vendor:orange}") String activeVendor) {
        this.gatewaysByVendor = gateways.stream()
                .collect(Collectors.toMap(SmsGateway::vendor, Function.identity()));
        this.activeVendor = activeVendor;
        log.info("SMS gateways registered: {}. Active vendor: {}", gatewaysByVendor.keySet(), activeVendor);
    }

    public SmsGateway getActiveGateway() {
        SmsGateway gateway = gatewaysByVendor.get(activeVendor);
        if (gateway == null) {
            throw new IllegalStateException(
                    "No SmsGateway registered for vendor '" + activeVendor + "'. Known vendors: " + gatewaysByVendor.keySet());
        }
        return gateway;
    }
}
