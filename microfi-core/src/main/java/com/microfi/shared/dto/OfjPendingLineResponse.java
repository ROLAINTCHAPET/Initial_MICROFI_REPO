package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * An active agent who has collected cash today but has no {@code OfjAgentLine} yet — i.e. hasn't
 * been reconciled at all. Backs the cashier's "who do I still need to reconcile" queue, which
 * {@code OfjAgentLineResponse} alone can't answer since that only exists once reconciliation has
 * already happened once (see {@code OfjService#reconcile}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfjPendingLineResponse {
    private UUID agentId;
    private long collectionsTotalXaf;
    private long activationsTotalXaf;
    private long digitalTotalXaf;
}
