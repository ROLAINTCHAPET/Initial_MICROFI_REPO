package com.microfi.savings.domain;

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
 * A one-time code issued for {@code POST /auth/client/forgot-password} and checked by
 * {@code POST /auth/client/reset-password} — the client-self-service PIN reset, mirroring
 * {@code com.microfi.authentication.domain.PasswordResetOtp}'s agent flow exactly, but kept as its
 * own table/entity rather than a shared polymorphic one: {@code savings} has no business reaching
 * into {@code authentication}'s repository, and the two flows (password vs. PIN, different actor
 * tables) are independent enough that sharing a schema would only couple them for no benefit.
 * {@code otpHash} is bcrypt, via the same {@code PasswordEncoder} bean as every other secret in
 * this app, never the plaintext code. A row is never reused: requesting a new code always inserts
 * a fresh one, and {@link #consumedAt} marks a code as spent the moment it succeeds.
 */
@Entity
@Table(name = "client_password_reset_otp", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientPasswordResetOtp {

    @Id
    private UUID id;

    private UUID clientId;

    private String otpHash;

    private Instant expiresAt;

    private Instant consumedAt;

    /** Failed match attempts against this specific code — a fresh code (see class doc) resets the count. */
    @Builder.Default
    private Integer attempts = 0;

    private Instant createdAt;
}
