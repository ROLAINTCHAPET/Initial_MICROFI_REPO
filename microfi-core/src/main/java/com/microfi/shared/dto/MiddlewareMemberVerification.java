package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of {@code POST /mw/v1/members/verify} — validates a CBS Activation ID / member. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareMemberVerification {
    private boolean verified;
    private String memberId;
    private String fullName;
    private String status;
}
