package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** UC-19 / BR-04: client-side half of the activation gate — PIN re-entry authorizes the fee debit. */
@Data
public class ClientPaymentConfirmationRequest {

    @NotBlank
    private String pin;
}
