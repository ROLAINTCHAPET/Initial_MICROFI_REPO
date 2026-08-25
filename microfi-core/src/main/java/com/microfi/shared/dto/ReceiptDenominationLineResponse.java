package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDenominationLineResponse {
    private long faceValueXaf;
    private int quantity;
    private long lineTotalXaf;
}
