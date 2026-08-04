package com.microfi.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Per-agent reconciliation line within an {@link OfjSession}. Delta = physical - digital (BR-01). */
@Entity
@Table(name = "ofj_agent_line", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfjAgentLine {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ofjId;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private long digitalTotalXaf;

    @Column(nullable = false)
    private long physicalTotalXaf;

    @Column(nullable = false)
    private long deltaXaf;
}
