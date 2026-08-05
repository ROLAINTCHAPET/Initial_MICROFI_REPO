package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class OfjAgentLineResponse {
    private UUID id;
    private UUID agentId;
    /** collectionsTotalXaf + activationsTotalXaf. */
    private long digitalTotalXaf;
    private long collectionsTotalXaf;
    private long activationsTotalXaf;
    private long physicalTotalXaf;
    private long deltaXaf;
    /** true if delta >= 0 (surplus/exact) or a variance debt has been recorded for a shortage. */
    private boolean resolved;
}
