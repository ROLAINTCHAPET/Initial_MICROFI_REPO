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
    private String details;
    private AuditStatus status;
}
