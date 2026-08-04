package com.microfi.mw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "idempotency_key", schema = "mw")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(name = "`key`")
    private String key;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
