package com.microfi.audit.domain;

import com.microfi.authentication.domain.AdminRole;
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
 * The "Security &amp; Administrative Trails" pillar of the compliance audit export
 * (dev_plan.txt lists Audit as its own module under both Backend and Back Office, never
 * previously built) — one row per meaningful lifecycle/security/decision event, for any actor
 * type (admin, agent, or client), for actions that otherwise leave no trace beyond overwriting a
 * current-state column (e.g. {@code Agent.status}). Financial and Synchronization data is
 * deliberately NOT duplicated here — it already lives, immutable and timestamped, in
 * {@code Collection}/{@code EscrowLedger}/{@code ActivationPayment}; this table would only add
 * noise at collection volume. Append-only, same "no soft-deletes on financial/audit facts" rule
 * the rest of this codebase follows.
 */
@Entity
@Table(name = "audit_log", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditCategory category;

    /** Short machine code, e.g. {@code AGENT_SUSPENDED}, {@code CLIENT_LOGIN_FAILED} — the human-readable description lives in {@link #details}. */
    @Column(nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditActorType actorType;

    /** Agent id, admin_user id, or client id depending on {@link #actorType} — null for AuditActorType.SYSTEM. */
    private UUID actorId;

    /** Resolved display name at the time of the event — the actor's own name/login can change or be deleted later, same reasoning as {@code VarianceDebt.writtenOffBy} needing this table to outlive the account it names. */
    @Column(nullable = false)
    private String actorLabel;

    /**
     * The specific back-office role (ADMIN/BRANCH_MANAGER/BRANCH_CASHIER) at the time of the
     * event, when {@link #actorType} is ADMIN — {@code actorType} alone only distinguishes the
     * admin/agent/client family, not which of the three back-office roles actually acted, so a
     * caller display resolved from actorType alone reads as a vague "Admin / Branch Manager /
     * Cashier" instead of the one role that's true. Null when actorType isn't ADMIN, or when a
     * failed login never resolved to a real account (no role to know).
     */
    @Enumerated(EnumType.STRING)
    private AdminRole actorRole;

    /** For branch-scoped visibility (BRANCH_MANAGER/BRANCH_CASHIER only ever see their own branch's rows). Null for a network-wide event with no single branch (e.g. a global schedule-defaults change). */
    private UUID branchId;

    /** The agent this event is about, when applicable — distinct from {@link #actorId}, since an admin acting ON an agent is a different person from the agent itself logging in. */
    private UUID agentId;

    /** The back-office account this event is about, when applicable (e.g. one admin resetting another's password). */
    private UUID targetAdminUserId;

    /**
     * Legacy free-text description, written in whatever language the server happened to compose
     * it in (always English, historically) — a viewer's locale can never change it after the
     * fact. New rows should always set {@link #detailsKey} instead, which the frontend renders
     * through its own English/French template so the same row reads correctly in either
     * language; {@code details} stays only as the rendering fallback for rows written before
     * {@link #detailsKey} existed, and is nullable for that reason.
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /** Machine code naming a frontend-rendered, parametrized template (e.g. {@code LOGIN_SUCCEEDED}) — see AuditExplorer's detailsTemplates map. Null only for legacy rows predating this field. */
    private String detailsKey;

    /** Positional substitution values for {@link #detailsKey}'s template placeholders ({@code {param1}}, {@code {param2}}, {@code {param3}}) — free-text values (reasons, names, amounts) that are data, not translatable UI copy, so they pass through unchanged regardless of viewer locale. */
    @Column(columnDefinition = "TEXT")
    private String detailsParam1;

    @Column(columnDefinition = "TEXT")
    private String detailsParam2;

    @Column(columnDefinition = "TEXT")
    private String detailsParam3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditStatus status;
}
