package com.microfi.shared.dto;

import com.microfi.authentication.domain.AgentStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AgentResponse {
    private UUID id;
    private String employeeCode;
    private String fullName;
    private String phone;
    private String imei;
    private UUID branchId;
    private AgentStatus status;
}
