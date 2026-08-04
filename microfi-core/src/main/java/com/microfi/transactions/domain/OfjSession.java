package com.microfi.transactions.domain;

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
import java.time.LocalDate;
import java.util.UUID;

/** One open session per branch/day (UC-16). */
@Entity
@Table(name = "ofj_session", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfjSession {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OfjSessionStatus status = OfjSessionStatus.OPEN;

    @Builder.Default
    private Instant openedAt = Instant.now();

    private Instant closedAt;
}
