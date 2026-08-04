package com.microfi.mw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cbs_call_log", schema = "mw")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbsCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String vendor;

    private Integer httpStatus;

    private Long durationMs;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
