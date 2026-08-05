package com.microfi.shared.dto;

import com.microfi.savings.domain.ClientStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ClientResponse {
    private UUID id;
    private String mfiMemberNo;
    private String fullName;
    private String phone;
    private UUID branchId;
    private ClientStatus status;
}
