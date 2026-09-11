package com.microfi.transactions.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A client-submitted report that their agent may have misbehaved — same "accept best-effort, let
 * Back-Office triage" shape as {@link SosEvent}, but the other direction (client reporting on an
 * agent rather than an agent raising their own distress). {@code reviewedBy}/{@code reviewedAt}
 * mirror SosEvent's acknowledgedBy/acknowledgedAt: null means still open.
 */
@Entity
@Table(name = "agent_misconduct_report", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMisconductReport {

    @Id
    private UUID id;

    private UUID agentId;

    private UUID clientId;

    private String reason;

    private Instant reportedAt;

    private UUID reviewedBy;

    private Instant reviewedAt;
}
