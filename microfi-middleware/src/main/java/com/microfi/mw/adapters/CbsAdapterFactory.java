package com.microfi.mw.adapters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the active {@link CoreBankingAdapter} for the configured CBS vendor.
 * New vendors register themselves as Spring beans and become selectable purely
 * via the {@code cbs.vendor} property, without touching call sites.
 */
@Component
@Slf4j
public class CbsAdapterFactory {

    private final Map<String, CoreBankingAdapter> adaptersByVendor;
    private final String activeVendor;

    public CbsAdapterFactory(List<CoreBankingAdapter> adapters, @Value("${cbs.vendor:mock}") String activeVendor) {
        this.adaptersByVendor = adapters.stream()
                .collect(Collectors.toMap(CoreBankingAdapter::vendor, Function.identity()));
        this.activeVendor = activeVendor;
        log.info("CBS adapters registered: {}. Active vendor: {}", adaptersByVendor.keySet(), activeVendor);
    }

    public CoreBankingAdapter getActiveAdapter() {
        CoreBankingAdapter adapter = adaptersByVendor.get(activeVendor);
        if (adapter == null) {
            throw new IllegalStateException(
                    "No CoreBankingAdapter registered for vendor '" + activeVendor + "'. Known vendors: " + adaptersByVendor.keySet());
        }
        return adapter;
    }
}
