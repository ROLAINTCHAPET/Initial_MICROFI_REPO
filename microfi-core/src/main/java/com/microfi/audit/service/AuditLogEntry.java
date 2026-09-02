package com.microfi.audit.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.authentication.domain.AdminRole;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/** What a caller hands {@link AuditService#record} — every field but the required ones is nullable; the service fills in id/occurredAt itself. */
@Value
@Builder
public class AuditLogEntry {
    AuditCategory category;
    String eventType;
    AuditActorType actorType;
    UUID actorId;
    String actorLabel;
    /** The specific back-office role at the time of the event, when actorType is ADMIN — see {@code AuditLog#actorRole}. */
    AdminRole actorRole;
    UUID branchId;
    UUID agentId;
    UUID targetAdminUserId;
    String details;
    @Builder.Default
    AuditStatus status = AuditStatus.SUCCESS;
}
