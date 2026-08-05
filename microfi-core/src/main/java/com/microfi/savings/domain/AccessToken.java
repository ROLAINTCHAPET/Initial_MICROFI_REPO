package com.microfi.savings.domain;

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
 * The 365-day digital booklet token (architecture.txt core.access_token, UC-19). A client has at
 * most one non-{@link AccessTokenStatus#REVOKED} token at a time; expiry is derived from
 * {@code expiresAt} rather than a separate EXPIRED status, since an expired token stays visible
 * and read-only consultable per UC-20/21/22 rather than disappearing.
 */
@Entity
@Table(name = "access_token", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessToken {

    @Id
    private UUID id;

    private UUID clientId;

    private Instant issuedAt;

    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccessTokenStatus status = AccessTokenStatus.ACTIVE;
}
