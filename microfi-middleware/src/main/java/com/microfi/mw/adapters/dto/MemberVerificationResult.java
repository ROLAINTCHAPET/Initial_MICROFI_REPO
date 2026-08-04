package com.microfi.mw.adapters.dto;

public record MemberVerificationResult(
    boolean verified,
    String memberId,
    String fullName,
    String status
) {
}
