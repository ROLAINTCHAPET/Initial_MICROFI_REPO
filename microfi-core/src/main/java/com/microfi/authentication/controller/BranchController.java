package com.microfi.authentication.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.shared.dto.BranchRequest;
import com.microfi.shared.dto.BranchResponse;
import com.microfi.shared.dto.ScheduleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
                            .timezone(request.getTimezone())
                            .build();
                    return toResponse(branchRepository.save(branch));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping
    @Operation(summary = "List Branches", description = "Any Back-Office role.")
    public Flux<BranchResponse> list(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(branchRepository::findAll)
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
    @Operation(summary = "Configure Branch Schedule", description = "Sets the session opening/closing time windows enforced on agent sessions (FR-15). ADMIN or that branch's own BRANCH_MANAGER.")
    public Mono<BranchResponse> putSchedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, id);
                    return Mono.fromCallable(() -> {
                        if (!request.getOpenTime().isBefore(request.getCloseTime())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openTime must be before closeTime");
                        }
                        Branch branch = findBranchOrThrow(id);
                        branch.setOpenTime(request.getOpenTime());
                        branch.setCloseTime(request.getCloseTime());
                        return toResponse(branchRepository.save(branch));
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
                .openTime(branch.getOpenTime())
                .closeTime(branch.getCloseTime())
                .timezone(branch.getTimezone())
                .build();
    }
}
