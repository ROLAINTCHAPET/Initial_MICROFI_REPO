package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** A client who's self-activated (set their own login) but has no live booklet token yet — UC-19 step 2 candidate list. */
@Data
@Builder
public class PendingClientActivationResponse {
    private UUID id;
    private String mfiMemberNo;
    private String fullName;
    private String phone;
    /** True if an agent has already sponsored this client and it's now only waiting on the client's own payment confirmation. */
    private boolean sponsored;
}
