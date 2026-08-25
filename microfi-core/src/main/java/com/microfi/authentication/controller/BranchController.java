package com.microfi.authentication.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.domain.BranchScheduleDefaults;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.repository.BranchScheduleDefaultsRepository;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.notifications.service.NotificationService;
import com.microfi.shared.dto.BranchDefaultCeilingPctRequest;
import com.microfi.shared.dto.BranchMaxCashiersRequest;
import com.microfi.shared.dto.BranchPhoneRequest;
import com.microfi.shared.dto.BranchRequest;
import com.microfi.shared.dto.BranchRequireImeiRequest;
import com.microfi.shared.dto.BranchResponse;
import com.microfi.shared.dto.ScheduleDefaultsResponse;
import com.microfi.shared.dto.ScheduleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * UC-15 — Branch Working-Hours Configuration. Schedule sub-resource mirrors architecture.txt
 * section 11.1: {@code GET/PUT /admin/branches/{id}/schedule}. Branch create/list are not
 * individually tabled in the doc but are required to have a branch to configure at all.
 * Creating a branch is ADMIN-only (org-level); schedule changes are ADMIN or that branch's own
 * BRANCH_MANAGER.
 */
@RestController
@RequestMapping("/api/v1/admin/branches")
@RequiredArgsConstructor
@Tag(name = "Branch Management", description = "Endpoints for branch registry and working-hours configuration")
public class BranchController {

    private final BranchRepository branchRepository;
    private final BranchScheduleDefaultsRepository scheduleDefaultsRepository;
    private final AgentDirectoryService agentDirectoryService;
    private final NotificationService notificationService;

    @GetMapping("/schedule-defaults")
    @Operation(summary = "Get Global Schedule Defaults", description = "Organization-wide default working hours (FR-15). Falls back to 08:00-17:00 until an ADMIN sets one explicitly. Any Back-Office role.")
    public Mono<ScheduleDefaultsResponse> getScheduleDefaults(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(this::currentDefaultsOrFallback)
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/schedule-defaults")
    @Operation(summary = "Set Global Schedule Defaults", description = "Updates the organization-wide default working hours. By default this only fills in branches that have no schedule of their own yet (explicit per-branch configuration wins). Pass overrideAll=true to force every branch, including ones with an existing schedule, to this window in one action. ADMIN only.")
    public Mono<ScheduleDefaultsResponse> putScheduleDefaults(
            @Valid @RequestBody ScheduleRequest request,
            @RequestParam(defaultValue = "false") boolean overrideAll,
            Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    if (!request.getOpenTime().isBefore(request.getCloseTime())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openTime must be before closeTime");
                    }
                    BranchScheduleDefaults defaults = scheduleDefaultsRepository.findById(BranchScheduleDefaults.SINGLETON_ID)
                            .orElse(BranchScheduleDefaults.builder().id(BranchScheduleDefaults.SINGLETON_ID).build());
                    defaults.setOpenTime(request.getOpenTime());
                    defaults.setCloseTime(request.getCloseTime());
                    defaults.setUpdatedAt(Instant.now());
                    scheduleDefaultsRepository.save(defaults);

                    List<Branch> targets = branchRepository.findAll().stream()
                            .filter(b -> overrideAll || b.getOpenTime() == null || b.getCloseTime() == null)
                            .peek(b -> {
                                b.setOpenTime(request.getOpenTime());
                                b.setCloseTime(request.getCloseTime());
                            })
                            .toList();
                    branchRepository.saveAll(targets);

                    return toDefaultsResponse(defaults);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Branch", description = "Registers a new branch. Working hours are configured separately via the schedule sub-resource. ADMIN only.")
    public Mono<BranchResponse> create(@Valid @RequestBody BranchRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    if (branchRepository.existsByCode(request.getCode())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Branch with code '" + request.getCode() + "' already exists");
                    }
                    Branch branch = Branch.builder()
                            .id(UUID.randomUUID())
                            .code(request.getCode())
                            .name(request.getName())
                            .phone(request.getPhone())
                            .timezone(request.getTimezone())
                            .build();
                    return toResponse(branchRepository.save(branch));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/phone")
    @Operation(summary = "Set Branch Contact Number", description = "The number field agents reach via \"Contact Branch\" in the mobile app. ADMIN or that branch's own BRANCH_MANAGER.")
    public Mono<BranchResponse> setPhone(@PathVariable UUID id, @Valid @RequestBody BranchPhoneRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, id);
                    return Mono.fromCallable(() -> {
                        Branch branch = findBranchOrThrow(id);
                        branch.setPhone(request.getPhone());
                        return toResponse(branchRepository.save(branch));
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @PatchMapping("/{id}/max-cashiers")
    @Operation(summary = "Set Branch Cashier Cap", description = "Maximum number of active BRANCH_CASHIER accounts this branch may hold at once; enforced by admin user creation. ADMIN or that branch's own BRANCH_MANAGER.")
    public Mono<BranchResponse> setMaxCashiers(@PathVariable UUID id, @Valid @RequestBody BranchMaxCashiersRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, id);
                    return Mono.fromCallable(() -> {
                        Branch branch = findBranchOrThrow(id);
                        branch.setMaxCashiers(request.getMaxCashiers());
                        return toResponse(branchRepository.save(branch));
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @PatchMapping("/{id}/require-imei")
    @Operation(summary = "Set Branch IMEI Requirement", description = "Whether enrolling a new agent at this branch must supply a device IMEI (device-binding). Disable for branches where agents use their own phone to collect. Does not affect already-enrolled agents. ADMIN or that branch's own BRANCH_MANAGER.")
    public Mono<BranchResponse> setRequireImei(@PathVariable UUID id, @Valid @RequestBody BranchRequireImeiRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, id);
                    return Mono.fromCallable(() -> {
                        Branch branch = findBranchOrThrow(id);
                        branch.setRequireImei(request.getRequireImei());
                        return toResponse(branchRepository.save(branch));
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @PatchMapping("/{id}/default-ceiling-pct")
    @Operation(summary = "Set Branch Default Ceiling Percentage", description = "How much escrow ceiling an agent at this branch is granted per XAF of security deposit funded via top-up (e.g. 100 = 1:1, the default; 150 = ceiling 1.5x the deposit). Applies to every future top-up for every agent at this branch — doesn't touch already-funded ceilings or retroactively change a temporary waiver, and admins/managers can still fund or waiver-override any individual agent's ceiling directly. ADMIN or that branch's own BRANCH_MANAGER.")
    public Mono<BranchResponse> setDefaultCeilingPct(@PathVariable UUID id, @Valid @RequestBody BranchDefaultCeilingPctRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, id);
                    return Mono.fromCallable(() -> {
                        Branch branch = findBranchOrThrow(id);
                        branch.setDefaultCeilingPct(request.getDefaultCeilingPct());
                        return toResponse(branchRepository.save(branch));
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @GetMapping
    @Operation(summary = "List Branches", description = "Any Back-Office role.")
    public Flux<BranchResponse> list(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(branchRepository::findAllByOrderByNameAsc)
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @GetMapping("/{id}/schedule")
    @Operation(summary = "Get Branch Schedule", description = "Session opening/closing time windows (FR-15). Any Back-Office role.")
    public Mono<BranchResponse> getSchedule(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> toResponse(findBranchOrThrow(id)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/{id}/schedule")
    @Operation(summary = "Configure Branch Schedule", description = "Sets the session opening/closing time windows enforced on agent sessions (FR-15). Once today's opening time has passed (branch's own timezone), openTime is locked for the rest of the day — only closeTime can still be changed; check openTimeLocked on GET before submitting. Changing closeTime notifies every agent at the branch (SMS + in-app notice). ADMIN or that branch's own BRANCH_MANAGER.")
    public Mono<BranchResponse> putSchedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, id);
                    return Mono.fromCallable(() -> {
                        if (!request.getOpenTime().isBefore(request.getCloseTime())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openTime must be before closeTime");
                        }
                        Branch branch = findBranchOrThrow(id);

                        boolean openTimeChanged = !java.util.Objects.equals(branch.getOpenTime(), request.getOpenTime());
                        if (openTimeChanged && agentDirectoryService.isBranchPastOpenTime(branch)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Today's opening time (" + branch.getOpenTime() + ") has already passed and can no longer be changed — only closing time can still be updated today.");
                        }
                        boolean closeTimeChanged = !java.util.Objects.equals(branch.getCloseTime(), request.getCloseTime());

                        branch.setOpenTime(request.getOpenTime());
                        branch.setCloseTime(request.getCloseTime());
                        Branch saved = branchRepository.save(branch);

                        if (closeTimeChanged) {
                            notificationService.notifyBranchScheduleChange(saved.getId(), saved.getName(), saved.getCloseTime());
                        }
                        return toResponse(saved);
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    private Branch findBranchOrThrow(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + id));
    }

    private BranchResponse toResponse(Branch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .code(branch.getCode())
                .name(branch.getName())
                .phone(branch.getPhone())
                .openTime(branch.getOpenTime())
                .closeTime(branch.getCloseTime())
                .openTimeLocked(agentDirectoryService.isBranchPastOpenTime(branch))
                .timezone(branch.getTimezone())
                .maxCashiers(branch.effectiveMaxCashiers())
                .requireImei(branch.effectiveRequireImei())
                .defaultCeilingPct(branch.effectiveDefaultCeilingPct())
                .build();
    }

    private ScheduleDefaultsResponse currentDefaultsOrFallback() {
        return scheduleDefaultsRepository.findById(BranchScheduleDefaults.SINGLETON_ID)
                .map(this::toDefaultsResponse)
                .orElse(ScheduleDefaultsResponse.builder()
                        .openTime(LocalTime.of(8, 0))
                        .closeTime(LocalTime.of(17, 0))
                        .build());
    }

    private ScheduleDefaultsResponse toDefaultsResponse(BranchScheduleDefaults defaults) {
        return ScheduleDefaultsResponse.builder()
                .openTime(defaults.getOpenTime())
                .closeTime(defaults.getCloseTime())
                .updatedAt(defaults.getUpdatedAt())
                .build();
    }
}
