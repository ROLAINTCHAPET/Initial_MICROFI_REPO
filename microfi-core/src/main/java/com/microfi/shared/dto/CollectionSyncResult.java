package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollectionSyncResult {
    private String deviceTxId;
    private boolean success;
    private String error;
    private CollectionResponse collection;
}
