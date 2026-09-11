package com.microfi.mw.adapters.dto;

public record MemberLookupResult(
    boolean found,
    String accountNumber,
    String fullName,
    String email,
    String phone
) {
}
