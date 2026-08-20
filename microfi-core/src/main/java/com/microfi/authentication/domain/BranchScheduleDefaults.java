package com.microfi.authentication.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Organization-wide default working hours (FR-15 "Global Thresholds"). Singleton row, keyed by
 * {@link #SINGLETON_ID}. Applied to any branch that has no schedule of its own configured yet —
 * see {@code BranchController#putScheduleDefaults}.
 */
@Entity
@Table(name = "branch_schedule_defaults", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchScheduleDefaults {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    private LocalTime openTime;

    private LocalTime closeTime;

    private Instant updatedAt;
}
