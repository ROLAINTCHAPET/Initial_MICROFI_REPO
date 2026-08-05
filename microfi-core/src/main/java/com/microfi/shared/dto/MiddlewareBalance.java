package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Result of {@code POST /mw/v1/members/balance} — live balance from the CBS (FR-21). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareBalance {
    private String memberId;
    private long balanceXaf;
    private Instant asOf;
}
