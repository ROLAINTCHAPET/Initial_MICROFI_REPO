package com.microfi.shared.dto;

import com.microfi.savings.domain.ClientStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ClientResponse {
    private UUID id;
    private String mfiMemberNo;
    private String fullName;
    private String phone;
    private String email;
    private UUID branchId;
    private ClientStatus status;
    /** True once an admin/branch-manager has confirmed this mirror row against the real CBS record — see ClientProfile#cbsSyncedAt. */
    private boolean cbsSynced;
    private Instant cbsSyncedAt;
    /** Whether this client can already log in (login/PIN set) — lets the Back-Office show "credentials set" without exposing the hash. */
    private boolean hasCredentials;

    /** "Portefeuille client" — null means unassigned (open to any agent in the branch). See Branch#requireClientPortfolio. */
    private UUID assignedAgentId;
}
