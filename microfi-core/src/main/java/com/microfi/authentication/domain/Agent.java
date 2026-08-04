package com.microfi.authentication.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "agent", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {

    @Id
    private UUID id;

    private UUID branchId;

    private String employeeCode;

    private String fullName;

    private String phone;
    
    private String imei;

    @Enumerated(EnumType.STRING)
    private AgentStatus status;

    private String pinHash;
}
