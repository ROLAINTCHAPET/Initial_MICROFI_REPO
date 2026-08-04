package com.microfi.mw.service;

import com.microfi.mw.domain.CbsCallLog;
import com.microfi.mw.repository.CbsCallLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Wraps every outbound CBS call with an audit row (correlation id, vendor, latency, outcome)
 * so a collection can be traced end-to-end from Core through Middleware to the CBS.
 */
@Service
@RequiredArgsConstructor
public class CbsCallLogger {

    private final CbsCallLogRepository cbsCallLogRepository;

    public <T> T logged(String correlationId, String operation, String vendor, Supplier<T> call) {
        long start = System.currentTimeMillis();
        int status = 200;
        try {
            return call.get();
        } catch (RuntimeException e) {
            status = 500;
            throw e;
        } finally {
            cbsCallLogRepository.save(CbsCallLog.builder()
                    .correlationId(correlationId)
                    .operation(operation)
                    .vendor(vendor)
                    .httpStatus(status)
                    .durationMs(System.currentTimeMillis() - start)
                    .build());
        }
    }
}
