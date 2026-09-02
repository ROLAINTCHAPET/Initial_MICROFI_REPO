package com.microfi.authentication.domain;

import jakarta.persistence.Column;
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
 * A physical device the system has recognized — a device is a property of the system, not of any
 * one agent (see AuthenticationController#login): once a device has been used successfully once,
 * by anyone, it stays usable by any agent from then on.
 */
@Entity
@Table(name = "terminal", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Terminal {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private UUID firstSeenByAgentId;

    @Column(nullable = false)
    private UUID lastSeenByAgentId;
}
