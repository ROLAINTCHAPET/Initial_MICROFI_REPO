package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** UC-22: one row of the client's contribution history / mini-statement. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientHistoryEntryResponse {
    private String reference;
    private long amountXaf;
    private Instant date;
    private String type;
}
