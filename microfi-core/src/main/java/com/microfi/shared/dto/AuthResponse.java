package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;

    /** Set only on the login that creates a brand-new AgentInstallationBinding (a first-ever login, or the first login after an admin reset) — the per-installation HMAC secret for the collection hash-chain, transmitted exactly once and never re-sent on a later login that merely matches the existing binding. Null otherwise. */
    private String installationSecret;
}
