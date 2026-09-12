package com.microfi.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An alertable, resolvable security event — distinct from {@code AuditLogEntry}/{@code AuditLog},
 * which is a write-once timeline with no resolved/unresolved concept. Every raise here also writes
 * a matching plain audit-log row (see SecurityEventService#raise) so {@code /admin/audit-log} stays
 * complete; this table is additive, the actionable queue an admin actually works from.
 */
@Entity
@Table(name = "security_event", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityEventType type;

    @Column(nullable = false)
    private Instant createdAt;

    /** Free-form forensic context, e.g. "expected installation=<x>, presented=<y>". */
    private String detail;

    private Instant resolvedAt;
    private UUID resolvedByAdminUserId;
    private String resolutionReason;
}
