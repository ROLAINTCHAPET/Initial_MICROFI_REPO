package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of {@code POST /mw/v1/fees/split} — activation fee split between agent and MFI (FR-19). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareFeeSplit {
    private long agentCommissionXaf;
    private long mfiShareXaf;
    private String reference;
}
