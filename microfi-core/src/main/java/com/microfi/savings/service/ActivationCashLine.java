package com.microfi.savings.service;

import java.time.Instant;
import java.util.UUID;

/** Cross-module view of an {@code ActivationPayment} for {@link ActivationDirectoryService} callers that need line-level detail, not just a sum. */
public record ActivationCashLine(UUID id, UUID clientId, long amountXaf, Instant paidAt) {
}
