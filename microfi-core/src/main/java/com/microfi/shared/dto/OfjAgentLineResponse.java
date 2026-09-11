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
    /** Collections under this line still awaiting the agent's own confirmation (see CollectionReconciliationStatus) — distinct from {@link #resolved}, which is purely about the physical count matching, not about the agent's sign-off. Excludes any collection whose rejection request has since been approved. */
    private long pendingConfirmationCount;
    /** Collections under this line voided by an approved rejection request — takes priority over {@link #pendingConfirmationCount} in the UI, since a rejected collection is no longer "awaiting" anything. */
    private long rejectedCount;
    /** Sum of exactly this line's CONFIRMED-and-not-voided collections — a repeat same-day sweep reuses the same line id, so this can be genuinely settled money sitting alongside a newer {@link #pendingConfirmationCount} batch on the very same line; {@link #physicalTotalXaf} is the line's whole-day cumulative figure and isn't usable for "what's actually validated" once the two mix. */
    private long confirmedTotalXaf;
    /** Collections under this line already agent-confirmed — see {@link #confirmedTotalXaf}. */
    private long confirmedCount;
    /** Sum of {@link #rejectedCount} collections' original recorded amount — what the agent/cashier had counted before the rejection was approved. 0 when {@link #rejectedCount} is 0. */
    private long rejectedActualTotalXaf;
    /** Sum of what the agent said the amount should actually have been, across {@link #rejectedCount} collections — only counts requests where the agent gave a corrected figure (amount-related reasons), so this can be less than {@link #rejectedCount} implies. 0 when none was given. */
    private long rejectedExpectedTotalXaf;
}
