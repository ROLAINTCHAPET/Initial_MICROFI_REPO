package com.microfi.shared.dto;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.authentication.domain.AdminRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AuditLogResponse {
    private UUID id;
    private Instant occurredAt;
    private AuditCategory category;
    private String eventType;
    private AuditActorType actorType;
    private String actorLabel;
    /** The actor's specific back-office role (ADMIN/BRANCH_MANAGER/BRANCH_CASHIER) at the time of the event — null when actorType isn't ADMIN, or the login never resolved to a real account. */
    private AdminRole actorRole;
    private UUID branchId;
    private String branchLabel;
    private UUID agentId;
    private String agentLabel;
    /** Legacy plain-text fallback, rendered as-is only when {@link #detailsKey} is null (a row written before it existed) — see {@code AuditLog#details}. */
    private String details;
    /** Names a frontend detailsTemplates entry the viewer's own dictionary renders in their language; params fill in its {@code {param1}}/{@code {param2}}/{@code {param3}} placeholders. */
    private String detailsKey;
    private String detailsParam1;
    private String detailsParam2;
    private String detailsParam3;
    private AuditStatus status;
}
