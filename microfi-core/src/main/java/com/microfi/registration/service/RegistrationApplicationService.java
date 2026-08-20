package com.microfi.registration.service;

import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AdminUserEnrollmentService;
import com.microfi.authentication.service.AgentEnrollmentService;
import com.microfi.notifications.domain.NotificationStatus;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.registration.domain.RegistrationApplication;
import com.microfi.registration.domain.RegistrationApplicationStatus;
import com.microfi.registration.domain.RegistrationAvailabilityField;
import com.microfi.registration.domain.RegistrationDocumentType;
import com.microfi.registration.domain.RegistrationTargetRole;
import com.microfi.registration.repository.RegistrationApplicationRepository;
import com.microfi.shared.dto.CreateAdminUserRequest;
import com.microfi.shared.dto.RegisterRequest;
import com.microfi.shared.dto.SubmitRegistrationApplicationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC-02-adjacent — compliance-gated enrollment. Bridges HR/legal document review and Core Banking
 * provisioning (architecture.txt's split of concerns): a submitted application sits in
 * {@code SUBMITTED} until an ADMIN approves or rejects it. Approval provisions the real account by
 * calling {@link AgentEnrollmentService}/{@link AdminUserEnrollmentService} unchanged — same
 * uniqueness checks, same {@code PENDING_CEILING}/zero-balance-escrow start state for agents, same
 * {@code mustChangePassword} start state for back-office accounts — so this is strictly a new
 * gate in front of enrollment, never a second implementation of it.
 */
@Service
@RequiredArgsConstructor
public class RegistrationApplicationService {

    private final RegistrationApplicationRepository registrationApplicationRepository;
    private final BranchRepository branchRepository;
    private final DocumentStorageService documentStorageService;
    private final TemporaryCredentialGenerator credentialGenerator;
    private final AgentEnrollmentService agentEnrollmentService;
    private final AdminUserEnrollmentService adminUserEnrollmentService;
    private final SmsGatewayFactory smsGatewayFactory;
    private final MfiSettingsService mfiSettingsService;
    private final KycFormatValidator kycFormatValidator;

    @Value("${registration.criminal-record.max-age-days:90}")
    private int criminalRecordMaxAgeDays;

    public Mono<RegistrationApplication> submit(SubmitRegistrationApplicationRequest request, UUID submittedBy,
                                                 Map<RegistrationDocumentType, FilePart> files) {
        return Mono.fromCallable(() -> buildDraft(request, submittedBy))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(application -> storeAllDocuments(application, files))
                .flatMap(application -> Mono.fromCallable(() -> registrationApplicationRepository.save(application))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private RegistrationApplication buildDraft(SubmitRegistrationApplicationRequest request, UUID submittedBy) {
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + request.getBranchId()));
        if (request.getTargetRole() == RegistrationTargetRole.AGENT
                && (request.getEmail() == null || request.getEmail().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required for an AGENT application");
        }
        if (request.getCriminalRecordIssuedDate() != null) {
            long ageDays = ChronoUnit.DAYS.between(request.getCriminalRecordIssuedDate(), LocalDate.now());
            if (ageDays < 0 || ageDays > criminalRecordMaxAgeDays) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Criminal record must be issued within the last " + criminalRecordMaxAgeDays + " days");
            }
        }
        if (!kycFormatValidator.isValidNationalId(request.getNationalIdNumber(), request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "National ID Number format looks invalid for this phone's country");
        }
        if (!kycFormatValidator.isValidTaxId(request.getTaxIdNumber(), request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax ID Number format looks invalid for this phone's country");
        }
        requireUnique(request);

        return RegistrationApplication.builder()
                .id(UUID.randomUUID())
                .targetRole(request.getTargetRole())
                .branchId(request.getBranchId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .login(request.getLogin())
                .email(request.getEmail())
                .employeeCode(request.getEmployeeCode())
                .nationalIdNumber(request.getNationalIdNumber())
                .taxIdNumber(request.getTaxIdNumber())
                .placeOfResidence(request.getPlaceOfResidence())
                .criminalRecordIssuedDate(request.getCriminalRecordIssuedDate())
                .status(RegistrationApplicationStatus.SUBMITTED)
                .submittedBy(submittedBy)
                .submittedAt(Instant.now())
                .build();
    }

    /**
     * No two agents/cashiers/managers may share a username, phone, email, National ID Number, or
     * Tax ID Number — checked here, at submission, as a final backstop. The same per-field checks
     * are also exposed live via {@link #isAvailable} (GET .../availability), called by the wizard
     * as each field is filled in — this is deliberately not the first time a duplicate surfaces;
     * it's the last-resort guarantee for whatever the live check missed (a race between two
     * simultaneous submissions, a caller that skipped the live check, etc.).
     */
    private void requireUnique(SubmitRegistrationApplicationRequest request) {
        if (isLoginTaken(request.getLogin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username '" + request.getLogin() + "' is already in use or pending review");
        }
        if (isPhoneTaken(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone '" + request.getPhone() + "' is already in use or pending review");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() && isEmailTaken(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email '" + request.getEmail() + "' is already in use or pending review");
        }
        if (request.getNationalIdNumber() != null && !request.getNationalIdNumber().isBlank() && isNationalIdTaken(request.getNationalIdNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "National ID Number is already in use or pending review");
        }
        if (request.getTaxIdNumber() != null && !request.getTaxIdNumber().isBlank() && isTaxIdTaken(request.getTaxIdNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax ID Number is already in use or pending review");
        }
    }

    /**
     * Live per-field uniqueness check backing GET .../availability — this is the one the wizard
     * actually calls as the person fills in each field, so a duplicate is caught immediately
     * instead of only at the final "Submit for Review" click. A blank value is always available
     * (both National ID and Tax ID are optional; the required fields are still enforced blank-or-not
     * by the form itself).
     */
    public boolean isAvailable(RegistrationAvailabilityField field, String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return switch (field) {
            case LOGIN -> !isLoginTaken(value);
            case PHONE -> !isPhoneTaken(value);
            case EMAIL -> !isEmailTaken(value);
            case NATIONAL_ID -> !isNationalIdTaken(value);
            case TAX_ID -> !isTaxIdTaken(value);
        };
    }

    private boolean isLoginTaken(String login) {
        return agentEnrollmentService.isUsernameTaken(login)
                || adminUserEnrollmentService.isLoginTaken(login)
                || registrationApplicationRepository.existsByLoginAndStatusNot(login, RegistrationApplicationStatus.REJECTED);
    }

    private boolean isPhoneTaken(String phone) {
        return agentEnrollmentService.isPhoneTaken(phone)
                || adminUserEnrollmentService.isPhoneTaken(phone)
                || registrationApplicationRepository.existsByPhoneAndStatusNot(phone, RegistrationApplicationStatus.REJECTED);
    }

    private boolean isEmailTaken(String email) {
        return agentEnrollmentService.isEmailTaken(email)
                || registrationApplicationRepository.existsByEmailAndStatusNot(email, RegistrationApplicationStatus.REJECTED);
    }

    private boolean isNationalIdTaken(String nationalId) {
        return registrationApplicationRepository.existsByNationalIdNumberAndStatusNot(nationalId, RegistrationApplicationStatus.REJECTED);
    }

    private boolean isTaxIdTaken(String taxId) {
        return registrationApplicationRepository.existsByTaxIdNumberAndStatusNot(taxId, RegistrationApplicationStatus.REJECTED);
    }

    private Mono<RegistrationApplication> storeAllDocuments(RegistrationApplication application, Map<RegistrationDocumentType, FilePart> files) {
        return Mono.zip(
                documentStorageService.store(application.getId(), RegistrationDocumentType.NATIONAL_ID, files.get(RegistrationDocumentType.NATIONAL_ID)),
                documentStorageService.store(application.getId(), RegistrationDocumentType.CRIMINAL_RECORD, files.get(RegistrationDocumentType.CRIMINAL_RECORD)),
                documentStorageService.store(application.getId(), RegistrationDocumentType.MEDICAL_FITNESS, files.get(RegistrationDocumentType.MEDICAL_FITNESS)),
                documentStorageService.store(application.getId(), RegistrationDocumentType.LOCATION_PLAN, files.get(RegistrationDocumentType.LOCATION_PLAN)),
                documentStorageService.store(application.getId(), RegistrationDocumentType.PASSPORT_PHOTO, files.get(RegistrationDocumentType.PASSPORT_PHOTO))
        ).map(paths -> {
            application.setNationalIdDocPath(paths.getT1());
            application.setCriminalRecordDocPath(paths.getT2());
            application.setMedicalFitnessDocPath(paths.getT3());
            application.setLocationPlanDocPath(paths.getT4());
            application.setPassportPhotoDocPath(paths.getT5());
            return application;
        });
    }

    public List<RegistrationApplication> list(UUID branchIdFilter, RegistrationApplicationStatus statusFilter) {
        if (branchIdFilter != null && statusFilter != null) {
            return registrationApplicationRepository.findByBranchIdAndStatus(branchIdFilter, statusFilter);
        }
        if (branchIdFilter != null) {
            return registrationApplicationRepository.findByBranchId(branchIdFilter);
        }
        if (statusFilter != null) {
            return registrationApplicationRepository.findByStatus(statusFilter);
        }
        return registrationApplicationRepository.findAll();
    }

    public RegistrationApplication get(UUID id) {
        return findOrThrow(id);
    }

    public String documentPath(RegistrationApplication application, RegistrationDocumentType type) {
        String path = switch (type) {
            case NATIONAL_ID -> application.getNationalIdDocPath();
            case CRIMINAL_RECORD -> application.getCriminalRecordDocPath();
            case MEDICAL_FITNESS -> application.getMedicalFitnessDocPath();
            case LOCATION_PLAN -> application.getLocationPlanDocPath();
            case PASSPORT_PHOTO -> application.getPassportPhotoDocPath();
        };
        if (path == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + type);
        }
        return path;
    }

    public record ApprovalResult(RegistrationApplication application, String tempPassword, String tempPin) {
    }

    public Mono<ApprovalResult> approve(UUID applicationId, UUID reviewerId, UUID replaceUserId) {
        return Mono.fromCallable(() -> provision(applicationId, reviewerId, replaceUserId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> sendActivationSms(result).thenReturn(result));
    }

    private ApprovalResult provision(UUID applicationId, UUID reviewerId, UUID replaceUserId) {
        RegistrationApplication application = findOrThrow(applicationId);
        if (application.getStatus() != RegistrationApplicationStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application is not pending review: " + application.getStatus());
        }

        String tempPassword = credentialGenerator.generatePassword();
        String tempPin = application.getTargetRole() == RegistrationTargetRole.AGENT ? credentialGenerator.generatePin() : null;

        if (application.getTargetRole() == RegistrationTargetRole.AGENT) {
            Agent agent = agentEnrollmentService.enroll(toRegisterRequest(application, tempPassword, tempPin));
            application.setProvisionedAgentId(agent.getId());
        } else {
            AdminUser adminUser = adminUserEnrollmentService.create(toCreateAdminUserRequest(application, tempPassword, replaceUserId));
            application.setProvisionedAdminUserId(adminUser.getId());
        }

        application.setStatus(RegistrationApplicationStatus.APPROVED);
        application.setReviewedBy(reviewerId);
        application.setReviewedAt(Instant.now());
        registrationApplicationRepository.save(application);
        return new ApprovalResult(application, tempPassword, tempPin);
    }

    private RegisterRequest toRegisterRequest(RegistrationApplication application, String tempPassword, String tempPin) {
        RegisterRequest request = new RegisterRequest();
        request.setEmployeeCode(application.getEmployeeCode());
        request.setFullName(fullName(application));
        request.setPhone(application.getPhone());
        request.setUsername(application.getLogin());
        request.setEmail(application.getEmail());
        request.setPassword(tempPassword);
        request.setPin(tempPin);
        request.setBranchId(application.getBranchId());
        return request;
    }

    private CreateAdminUserRequest toCreateAdminUserRequest(RegistrationApplication application, String tempPassword, UUID replaceUserId) {
        CreateAdminUserRequest request = new CreateAdminUserRequest();
        request.setLogin(application.getLogin());
        request.setPassword(tempPassword);
        request.setFullName(fullName(application));
        request.setPhone(application.getPhone());
        request.setRole(application.getTargetRole() == RegistrationTargetRole.BRANCH_MANAGER ? AdminRole.BRANCH_MANAGER : AdminRole.BRANCH_CASHIER);
        request.setBranchId(application.getBranchId());
        request.setReplaceUserId(replaceUserId);
        return request;
    }

    /** Agent/AdminUser both still model identity as a single fullName field — combine only at the provisioning boundary, never stored combined on the dossier itself. */
    private String fullName(RegistrationApplication application) {
        return application.getFirstName() + " " + application.getLastName();
    }

    /** Best-effort, mirrors NotificationService's send/persist shape — a gateway failure never blocks the approval that already happened. */
    private Mono<Void> sendActivationSms(ApprovalResult result) {
        RegistrationApplication application = result.application();
        String mfiName = mfiSettingsService.getName();
        String credentials = application.getTargetRole() == RegistrationTargetRole.AGENT
                ? "identifiant: " + application.getLogin() + ", mot de passe temporaire: " + result.tempPassword() + ", PIN: " + result.tempPin()
                : "identifiant: " + application.getLogin() + ", mot de passe temporaire: " + result.tempPassword();
        String message = mfiName + ": votre compte a ete approuve. " + credentials + ". A changer a la premiere connexion. Merci.";

        return smsGatewayFactory.getActiveGateway().send(application.getPhone(), message)
                .flatMap(smsResult -> Mono.fromCallable(() -> {
                    application.setActivationSmsStatus(smsResult.success() ? NotificationStatus.SENT : NotificationStatus.FAILED);
                    application.setActivationSmsSentAt(Instant.now());
                    return registrationApplicationRepository.save(application);
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    public RegistrationApplication reject(UUID applicationId, UUID reviewerId, String reason) {
        RegistrationApplication application = findOrThrow(applicationId);
        if (application.getStatus() != RegistrationApplicationStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application is not pending review: " + application.getStatus());
        }
        application.setStatus(RegistrationApplicationStatus.REJECTED);
        application.setReviewedBy(reviewerId);
        application.setReviewedAt(Instant.now());
        application.setRejectionReason(reason);
        return registrationApplicationRepository.save(application);
    }

    private RegistrationApplication findOrThrow(UUID id) {
        return registrationApplicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration application not found: " + id));
    }
}
