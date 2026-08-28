package com.microfi.authentication.domain;

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

@Entity
@Table(name = "agent", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {

    @Id
    private UUID id;

    private UUID branchId;

    private String employeeCode;

    private String fullName;

    private String phone;

    private String imei;

    /**
     * Audit trail for the last device-binding reset (admin/manager-only visibility — never
     * returned from {@code AgentSelfController}). Null until the first reset. See
     * {@code AgentManagementController#resetDeviceBinding}.
     */
    private String deviceResetReason;

    private Instant deviceResetAt;

    /** Login handle — distinct from {@link #employeeCode}, which stays an HR/business identifier only. */
    private String username;

    private String email;

    /** Login secret (bcrypt). Checked at {@code POST /auth/agent/login}, replacing PIN as the login credential. */
    private String passwordHash;

    /**
     * Transaction-confirmation secret (bcrypt) — checked on every {@code POST /collections}, not
     * at login. Admin assigns an initial value at enrollment; the agent must replace it with one
     * of their own (see {@link #pinMustChange}) before their first collection.
     */
    private String pinHash;

    /** True until the agent replaces the admin-assigned initial PIN with their own; blocks collections until then. */
    @Builder.Default
    private Boolean pinMustChange = true;

    /**
     * UC-01 lockout (design handoff §4.1): consecutive failed LOGIN (password) attempts, reset on
     * a successful login. Boxed/no {@code @Column(nullable=false)} — same reasoning as
     * {@code OfjAgentLine}'s boxed totals: a new non-nullable primitive column breaks
     * {@code ddl-auto=update} against an already-populated Postgres table.
     */
    @Builder.Default
    private Integer failedPinAttempts = 0;

    /** Non-null while locked; a failed login while this is in the future is rejected without even checking the password. */
    private Instant lockedUntil;

    /** Same lockout mechanics as {@link #failedPinAttempts}/{@link #lockedUntil}, but for the transaction PIN — a short numeric secret checked repeatedly deserves its own brute-force guard, independent of login. */
    @Builder.Default
    private Integer failedTransactionPinAttempts = 0;

    private Instant transactionPinLockedUntil;

    @Enumerated(EnumType.STRING)
    private AgentStatus status;

    /**
     * UC-16 OFJ close guard (design handoff §6.1): how many collections this agent's app
     * currently has queued locally, not yet synced — self-reported via
     * {@code PATCH /agents/{id}/sync-status} since the server has no way to know about a
     * collection that hasn't reached it yet.
     */
    @Builder.Default
    private Integer pendingSyncCount = 0;

    /** Populated only when {@link #status} is {@link AgentStatus#DELETED} — soft-delete, row is kept for audit. */
    private String deletionReason;
    private UUID deletedBy;
    private Instant deletedAt;
}
