package com.microfi.mw.controller;

import com.microfi.mw.adapters.dto.BalanceResult;
import com.microfi.mw.adapters.dto.HistoryEntry;
import com.microfi.mw.adapters.dto.MemberLookupResult;
import com.microfi.mw.adapters.dto.MemberVerificationResult;
import com.microfi.mw.adapters.generic.MockCbsMember;
import com.microfi.mw.adapters.generic.MockCbsMemberRepository;
import com.microfi.mw.api.CorrelationId;
import com.microfi.mw.api.dto.BalanceRequest;
import com.microfi.mw.api.dto.LookupMemberRequest;
import com.microfi.mw.api.dto.SeedMemberRequest;
import com.microfi.mw.api.dto.VerifyMemberRequest;
import com.microfi.mw.service.CbsIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mw/v1/members")
@RequiredArgsConstructor
@Tag(name = "Members", description = "CBS member verification, balance and history (internal, called by Core only)")
public class MemberController {

    private final CbsIntegrationService cbsIntegrationService;
    private final MockCbsMemberRepository mockCbsMemberRepository;

    @PostMapping("/verify")
    @Operation(summary = "Validate an Activation ID / member against the CBS")
    MemberVerificationResult verify(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                     @Valid @RequestBody VerifyMemberRequest request) {
        return cbsIntegrationService.verifyMember(CorrelationId.resolve(correlationId), request.activationId());
    }

    @PostMapping("/lookup")
    @Operation(summary = "Look Up A Member By Account Number", description = "Backs MICROFI's background client-mirror sync — reconciles a local client_profile row against the real CBS record by account number.")
    MemberLookupResult lookup(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                               @Valid @RequestBody LookupMemberRequest request) {
        return cbsIntegrationService.getMember(CorrelationId.resolve(correlationId), request.accountNumber());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Seed A Mock CBS Member (dev/test only)", description = "Upserts by account number in the mock CBS's own simulated member directory. Not part of the vendor-neutral CoreBankingAdapter contract — a real vendor would never expose this; MICROFI itself never creates CBS customers.")
    void seed(@Valid @RequestBody SeedMemberRequest request) {
        // No manually-assigned id here — MockCbsMember#id is @GeneratedValue; setting one before
        // persist makes Spring Data JPA treat a genuinely new row as an update of an existing one
        // (isNew() sees a non-null id), which threw StaleObjectStateException the first time this
        // ran against a fresh account number.
        MockCbsMember member = mockCbsMemberRepository.findByAccountNumber(request.accountNumber())
                .orElseGet(() -> MockCbsMember.builder().accountNumber(request.accountNumber()).build());
        member.setFullName(request.fullName());
        member.setEmail(request.email());
        member.setPhone(request.phone());
        mockCbsMemberRepository.save(member);
    }

    @PostMapping("/balance")
    @Operation(summary = "Fetch a member's live balance from the CBS")
    BalanceResult balance(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                           @Valid @RequestBody BalanceRequest request) {
        return cbsIntegrationService.getBalance(CorrelationId.resolve(correlationId), request.memberId());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Pull member contribution history from the CBS when the local cache is stale")
    List<HistoryEntry> history(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                @PathVariable("id") String memberId) {
        return cbsIntegrationService.getHistory(CorrelationId.resolve(correlationId), memberId);
    }
}
