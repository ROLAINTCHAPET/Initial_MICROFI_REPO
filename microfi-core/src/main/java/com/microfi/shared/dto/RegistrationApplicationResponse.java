package com.microfi.shared.dto;

import com.microfi.registration.domain.RegistrationApplicationStatus;
import com.microfi.registration.domain.RegistrationTargetRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RegistrationApplicationResponse {
    private UUID id;
    private RegistrationTargetRole targetRole;
    private UUID branchId;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String phone;
    private String login;
    private String email;
    private String employeeCode;
    private String nationalIdNumber;
    private String taxIdNumber;
    private String placeOfResidence;
    private LocalDate criminalRecordIssuedDate;
    private RegistrationApplicationStatus status;
    private UUID submittedBy;
    private Instant submittedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;
    private UUID provisionedAgentId;
    private UUID provisionedAdminUserId;
    private String activationSmsStatus;
    private Instant activationSmsSentAt;

    /**
     * Set once, only in the direct response to {@code PATCH /{id}/approve} — never persisted,
     * never returned by any GET. This is the only channel to retrieve the system-generated
     * temporary credential when local SMS sends are no-ops (no real carrier credentials
     * configured), so an ADMIN can relay it manually if needed.
     */
    private String tempPassword;

    /** Agent targets only; null for BRANCH_MANAGER/BRANCH_CASHIER approvals. Same one-time-only rule as {@link #tempPassword}. */
    private String tempPin;
}
