package com.microfi.shared.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AssignClientAgentRequest {

    /** Null clears the assignment, making the client open to any agent in the branch again. */
    private UUID agentId;
}
