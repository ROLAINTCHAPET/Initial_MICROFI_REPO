package com.microfi.notifications.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.shared.dto.MfiSettingsRequest;
import com.microfi.shared.dto.MfiSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Org identity used in notification content (BR-Notif-01's mandatory "MFI name" mention) —
 * admin-configurable rather than hardcoded, since MICROFI is a platform other MFIs run on, not a
 * name every deployment actually has.
 */
@RestController
@RequestMapping("/api/v1/admin/settings/mfi")
@RequiredArgsConstructor
@Tag(name = "MFI Settings", description = "Org-wide identity used in client-facing notification content")
public class MfiSettingsController {

    private final MfiSettingsService mfiSettingsService;

    @GetMapping
    @Operation(summary = "Get MFI Name", description = "Any Back-Office role.")
    public Mono<MfiSettingsResponse> get(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> MfiSettingsResponse.builder().name(mfiSettingsService.getName()).build())
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping
    @Operation(summary = "Set MFI Name", description = "Org-wide, not branch-scoped — ADMIN only.")
    public Mono<MfiSettingsResponse> update(@Valid @RequestBody MfiSettingsRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> Mono.fromCallable(() -> MfiSettingsResponse.builder().name(mfiSettingsService.updateName(request.getName())).build())
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
