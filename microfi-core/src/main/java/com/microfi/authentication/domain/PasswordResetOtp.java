package com.microfi.authentication.domain;

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
 * A one-time code issued for {@code POST /auth/agent/forgot-password} and checked by
 * {@code POST /auth/agent/reset-password} — the agent-self-service counterpart to the
 * admin-initiated {@code AgentManagementController#resetPassword}. {@code otpHash} is bcrypt, via
 * the same {@code PasswordEncoder} bean as every other secret in this app, never the plaintext
 * code. A row is never reused: requesting a new code always inserts a fresh one rather than
 * updating an existing row, and {@link #consumedAt} marks a code as spent the moment it succeeds.
 */
@Entity
@Table(name = "password_reset_otp", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetOtp {

    @Id
    private UUID id;

    private UUID agentId;

    private String otpHash;

    private Instant expiresAt;

    private Instant consumedAt;

    /** Failed match attempts against this specific code — a fresh code (see class doc) resets the count, unlike the login lockout's rolling counter. */
    @Builder.Default
    private Integer attempts = 0;

    private Instant createdAt;
}
