package com.microfi.registration.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.registration.domain.RegistrationApplication;
import com.microfi.registration.domain.RegistrationApplicationStatus;
import com.microfi.registration.domain.RegistrationAvailabilityField;
import com.microfi.registration.domain.RegistrationDocumentType;
import com.microfi.registration.service.DocumentStorageService;
import com.microfi.registration.service.RegistrationApplicationService;
import com.microfi.shared.dto.ApproveRegistrationApplicationRequest;
import com.microfi.shared.dto.AvailabilityResponse;
import com.microfi.shared.dto.RegistrationApplicationResponse;
import com.microfi.shared.dto.RejectRegistrationApplicationRequest;
import com.microfi.shared.dto.SubmitRegistrationApplicationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance-gated agent/cashier/manager registration — an additive layer in front of
 * {@code AgentManagementController#register}/{@code AdminUserManagementController#create}
 * (neither of which this touches): a submitted application with five document scans sits in
 * {@code SUBMITTED} until ADMIN approves (provisioning the real account via the same enrollment
 * services those controllers use) or rejects it.
 */
@RestController
@RequestMapping("/api/v1/admin/registration-applications")
@RequiredArgsConstructor
@Tag(name = "Registration Applications", description = "Compliance-gated digital enrollment dossiers for agents, cashiers, and branch managers")
public class RegistrationApplicationController {

    private final RegistrationApplicationService registrationApplicationService;
    private final DocumentStorageService documentStorageService;
    private final AuditService auditService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit Registration Application", description = "Multipart: a 'metadata' JSON part plus five document parts (nationalId, criminalRecord, medicalFitness, locationPlan, passportPhoto — PDF or JPEG only). Starts SUBMITTED, pending ADMIN review. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<RegistrationApplicationResponse> submit(
            @Valid @RequestPart("metadata") SubmitRegistrationApplicationRequest metadata,
            @RequestPart("nationalId") FilePart nationalId,
            @RequestPart("criminalRecord") FilePart criminalRecord,
            @RequestPart("medicalFitness") FilePart medicalFitness,
            @RequestPart("locationPlan") FilePart locationPlan,
            @RequestPart("passportPhoto") FilePart passportPhoto,
            Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, metadata.getBranchId());
                    Map<RegistrationDocumentType, FilePart> files = new EnumMap<>(RegistrationDocumentType.class);
                    files.put(RegistrationDocumentType.NATIONAL_ID, nationalId);
                    files.put(RegistrationDocumentType.CRIMINAL_RECORD, criminalRecord);
                    files.put(RegistrationDocumentType.MEDICAL_FITNESS, medicalFitness);
                    files.put(RegistrationDocumentType.LOCATION_PLAN, locationPlan);
                    files.put(RegistrationDocumentType.PASSPORT_PHOTO, passportPhoto);
                    return registrationApplicationService.submit(metadata, caller.getAdminUser().getId(), files)
                            .map(this::toResponse);
                });
    }

    @GetMapping
    @Operation(summary = "List Registration Applications", description = "ADMIN sees every application; BRANCH_MANAGER only their own branch's. Optional status filter.")
    public Flux<RegistrationApplicationResponse> list(@RequestParam(required = false) RegistrationApplicationStatus status,
                                                        Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMapMany(caller -> Mono.fromCallable(() -> {
                    UUID branchFilter = caller.getAdminUser().getRole() == AdminRole.ADMIN ? null : caller.getAdminUser().getBranchId();
                    return registrationApplicationService.list(branchFilter, status);
                }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @GetMapping("/availability")
    @Operation(summary = "Check Field Availability", description = "Live per-field uniqueness check (login/phone/email/nationalId/taxId) the wizard calls while a field is being filled in — so a duplicate surfaces immediately, not only at the final Submit. A blank value is always reported available. ADMIN or BRANCH_MANAGER.")
    public Mono<AvailabilityResponse> availability(@RequestParam RegistrationAvailabilityField field, @RequestParam(required = false) String value,
                                                     Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> registrationApplicationService.isAvailable(field, value))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(available -> AvailabilityResponse.builder().available(available).build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Registration Application", description = "ADMIN can view any application; BRANCH_MANAGER only their own branch's.")
    public Mono<RegistrationApplicationResponse> get(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    RegistrationApplication application = registrationApplicationService.get(id);
                    AdminAccess.requireBranchScope(caller, application.getBranchId());
                    return application;
                }).subscribeOn(Schedulers.boundedElastic()))
                .map(this::toResponse);
    }

    @GetMapping("/{id}/documents/{docType}")
    @Operation(summary = "Download Registration Document", description = "Streams one stored document scan. Never web-served directly — this is the only read path. ADMIN can view any application's documents; BRANCH_MANAGER only their own branch's.")
    public Mono<ResponseEntity<Resource>> document(@PathVariable UUID id, @PathVariable RegistrationDocumentType docType,
                                                     Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    RegistrationApplication application = registrationApplicationService.get(id);
                    AdminAccess.requireBranchScope(caller, application.getBranchId());
                    return registrationApplicationService.documentPath(application, docType);
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(path -> documentStorageService.load(path)
                        .map(resource -> ResponseEntity.ok().contentType(documentStorageService.contentTypeFor(path)).body(resource)));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve Registration Application", description = "Provisions the real Agent/AdminUser account (via the same enrollment logic AgentManagementController/AdminUserManagementController use — no auto-assigned escrow ceiling, agents still start PENDING_CEILING until a real top-up) and sends a best-effort activation SMS. The response's tempPassword/tempPin are returned exactly once here and never persisted or retrievable again — treat as sensitive. ADMIN only.")
    public Mono<RegistrationApplicationResponse> approve(@PathVariable UUID id,
                                                           @RequestBody(required = false) ApproveRegistrationApplicationRequest request,
                                                           Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> registrationApplicationService.approve(id, caller.getAdminUser().getId(),
                                request != null ? request.getReplaceUserId() : null)
                        .doOnNext(result -> auditService.record(AuditLogEntry.builder()
                                .category(AuditCategory.COMPLIANCE)
                                .eventType("REGISTRATION_APPROVED")
                                .actorType(AuditActorType.ADMIN)
                                .actorId(caller.getAdminUser().getId())
                                .actorLabel(caller.getAdminUser().getLogin())
                                .actorRole(caller.getAdminUser().getRole())
                                .branchId(result.application().getBranchId())
                                .detailsKey("REGISTRATION_APPROVED_DETAIL")
                                .detailsParam1(result.application().getFirstName() + " " + result.application().getLastName())
                                .detailsParam2(result.application().getTargetRole().name())
                                .build())))
                .map(result -> toResponse(result.application(), result.tempPassword(), result.tempPin()));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject Registration Application", description = "No account is provisioned. ADMIN only.")
    public Mono<RegistrationApplicationResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectRegistrationApplicationRequest request,
                                                          Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> Mono.fromCallable(() -> {
                            RegistrationApplication rejected = registrationApplicationService.reject(id, caller.getAdminUser().getId(), request.getReason());
                            auditService.record(AuditLogEntry.builder()
                                    .category(AuditCategory.COMPLIANCE)
                                    .eventType("REGISTRATION_REJECTED")
                                    .actorType(AuditActorType.ADMIN)
                                    .actorId(caller.getAdminUser().getId())
                                    .actorLabel(caller.getAdminUser().getLogin())
                                    .actorRole(caller.getAdminUser().getRole())
                                    .branchId(rejected.getBranchId())
                                    .detailsKey("REGISTRATION_REJECTED_DETAIL")
                                    .detailsParam1(rejected.getFirstName() + " " + rejected.getLastName())
                                    .detailsParam2(request.getReason())
                                    .build());
                            return rejected;
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(this::toResponse);
    }

    private RegistrationApplicationResponse toResponse(RegistrationApplication application) {
        return toResponse(application, null, null);
    }

    private RegistrationApplicationResponse toResponse(RegistrationApplication application, String tempPassword, String tempPin) {
        return RegistrationApplicationResponse.builder()
                .id(application.getId())
                .targetRole(application.getTargetRole())
                .branchId(application.getBranchId())
                .firstName(application.getFirstName())
                .lastName(application.getLastName())
                .dateOfBirth(application.getDateOfBirth())
                .phone(application.getPhone())
                .login(application.getLogin())
                .email(application.getEmail())
                .employeeCode(application.getEmployeeCode())
                .nationalIdNumber(application.getNationalIdNumber())
                .taxIdNumber(application.getTaxIdNumber())
                .placeOfResidence(application.getPlaceOfResidence())
                .criminalRecordIssuedDate(application.getCriminalRecordIssuedDate())
                .status(application.getStatus())
                .submittedBy(application.getSubmittedBy())
                .submittedAt(application.getSubmittedAt())
                .reviewedBy(application.getReviewedBy())
                .reviewedAt(application.getReviewedAt())
                .rejectionReason(application.getRejectionReason())
                .provisionedAgentId(application.getProvisionedAgentId())
                .provisionedAdminUserId(application.getProvisionedAdminUserId())
                .activationSmsStatus(application.getActivationSmsStatus() != null ? application.getActivationSmsStatus().name() : null)
                .activationSmsSentAt(application.getActivationSmsSentAt())
                .tempPassword(tempPassword)
                .tempPin(tempPin)
                .build();
    }
}
