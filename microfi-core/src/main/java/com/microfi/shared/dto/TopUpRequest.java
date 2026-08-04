package com.microfi.shared.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TopUpRequest {

    @Positive(message = "Top-up amount must be positive (BR-Escrow-01)")
    private long amountXaf;

    /** Payment reference or channel note. MVP supports manual cashier top-up only (BR-Escrow-02). */
    private String reference;
}
