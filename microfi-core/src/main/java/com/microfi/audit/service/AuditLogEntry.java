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
    /** Legacy free-text fallback — new call sites should set {@link #detailsKey} instead so the frontend can render it in the viewer's own language; see {@code AuditLog#details}. */
    String details;
    /** Machine code naming a frontend detailsTemplates entry — see {@code AuditLog#detailsKey}. */
    String detailsKey;
    String detailsParam1;
    String detailsParam2;
    String detailsParam3;
    @Builder.Default
    AuditStatus status = AuditStatus.SUCCESS;
}
