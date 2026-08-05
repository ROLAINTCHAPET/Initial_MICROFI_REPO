package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** UC-21: My Account balance — visible even if the booklet token has expired (FR-21). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBalanceResponse {
    private long balanceXaf;
    private Instant asOf;
}
