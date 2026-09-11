package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Pushed to ADMIN/BRANCH_MANAGER/BRANCH_CASHIER when the scheduled closing-time job posts something for their branch. */
@Data
@Builder
public class OfjClosingExportAlertResponse {
    private UUID branchId;
    private LocalDate businessDate;
    private int postedCount;
    private Instant exportedAt;
}
