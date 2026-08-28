package com.microfi.authentication.domain;

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
import java.util.UUID;

/**
 * Back-Office user (architecture.txt core.admin_user). {@code branchId} is null for
 * {@link AdminRole#ADMIN} (global scope) and required for {@code BRANCH_MANAGER}/{@code BRANCH_CASHIER}
 * (branch-scoped).
 */
@Entity
@Table(name = "admin_user", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminRole role;

    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AdminUserStatus status = AdminUserStatus.ACTIVE;

    private String fullName;

    private String phone;

    /**
     * True until the account holder replaces the admin-assigned starting password with one of
     * their own. Null (the default for every row created before this field existed, including
     * the bootstrap ADMIN) is treated as "not forced" — this only applies to accounts created
     * through {@code AdminUserManagementController#create} going forward.
     */
    private Boolean mustChangePassword;

    /** Populated only when {@link #status} is {@link AdminUserStatus#DELETED} — soft-delete, row is kept for audit. */
    private String deletionReason;
    private UUID deletedBy;
    private Instant deletedAt;
}
