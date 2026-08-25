package com.microfi.notifications.domain;

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
 * A branch-wide operational notice (e.g. "closing time changed to 18:00 today") that agents don't
 * otherwise have any way to learn about — there's no push infrastructure in this app, so the
 * mobile client polls {@code GET /agents/me/branch-notices} the same way it already polls for SOS
 * acknowledgement (see HomeScreen's foreground poll). SMS is sent alongside this for immediate
 * reach even with the app closed; this row is what makes the notice visible once the app is open,
 * including to an agent who missed the SMS.
 */
@Entity
@Table(name = "branch_notice", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchNotice {

    @Id
    private UUID id;

    private UUID branchId;

    private String message;

    private Instant createdAt;
}
