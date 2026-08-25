package com.microfi.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.microfi.notifications.domain.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A CEMAC/COBAC-compliant enrollment dossier: administrative fields, PII, and five document scan
 * paths, gated through compliance review before the real {@code Agent}/{@code AdminUser} account
 * is provisioned (see {@code RegistrationApplicationService}). Deliberately separate from those
 * operational entities — neither gets a single new field for this — so the compliance dossier
 * never bleeds into already-tested account records.
 */
@Entity
@Table(name = "registration_application", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationApplication {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationTargetRole targetRole;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String login;

    /** Required for AGENT targets, unused for BRANCH_MANAGER/BRANCH_CASHIER (AdminUser has no email field). */
    private String email;

    /** Agent-only, optional — mirrors RegisterRequest#employeeCode's default-to-username behavior. */
    private String employeeCode;

    private String nationalIdNumber;

    private String taxIdNumber;

    /** Free-text home address/neighborhood, e.g. "Akwa, Douala" — not wired into geofencing. */
    private String placeOfResidence;

    private LocalDate criminalRecordIssuedDate;

    private String nationalIdDocPath;

    private String criminalRecordDocPath;

    private String medicalFitnessDocPath;

    private String locationPlanDocPath;

    private String passportPhotoDocPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationApplicationStatus status;

    @Column(nullable = false)
    private UUID submittedBy;

    @Column(nullable = false)
    private Instant submittedAt;

    private UUID reviewedBy;

    private Instant reviewedAt;

    private String rejectionReason;

    /** Exactly one of these is set on approval, depending on {@link #targetRole}. */
    private UUID provisionedAgentId;

    private UUID provisionedAdminUserId;

    @Enumerated(EnumType.STRING)
    private NotificationStatus activationSmsStatus;

    private Instant activationSmsSentAt;
}
