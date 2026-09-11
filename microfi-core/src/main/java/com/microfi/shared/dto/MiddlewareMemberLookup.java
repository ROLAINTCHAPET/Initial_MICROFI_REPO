package com.microfi.shared.dto;

/** Mirrors the middleware's {@code MemberLookupResult} — a member looked up by CBS account number, for {@code ClientCbsSyncJob}. */
public record MiddlewareMemberLookup(
    boolean found,
    String accountNumber,
    String fullName,
    String email,
    String phone
) {
}
