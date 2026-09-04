package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EndDayResponse {
    private int exportedCount;
    private long exportedTotalXaf;
}
