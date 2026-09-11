package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * The agent's own transaction PIN, required to confirm a reconciliation line — same PIN
 * verification a collection itself requires (AgentDirectoryService#verifyTransactionPin), so
 * confirming genuinely proves it was this agent, not just whoever is holding an unlocked phone.
 */
@Data
public class ConfirmReconciliationRequest {

    @NotBlank
    private String pin;
}
