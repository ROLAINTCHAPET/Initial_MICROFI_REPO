package com.microfi.authentication.domain;

/**
 * {@code PENDING_APPROVAL} is the starting status for an account a BRANCH_MANAGER creates — an
 * ADMIN must explicitly approve it (see AdminUserManagementController#approve) before it can log
 * in; a manager cannot approve their own or another manager's creation. An ADMIN-created account
 * skips this state entirely and starts {@code ACTIVE}, same as before this status existed.
 */
public enum AdminUserStatus {
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED,
    DELETED
}
