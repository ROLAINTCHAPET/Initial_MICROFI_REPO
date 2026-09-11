package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BroadcastMessageRequest {

    /** "CLIENTS" or "AGENTS". */
    @NotNull
    private String audience;

    /** Null = network-wide (ADMIN only) — a BRANCH_MANAGER's send is always forced to their own branch regardless of what's sent here. */
    private UUID branchId;

    @NotBlank
    private String message;
}
