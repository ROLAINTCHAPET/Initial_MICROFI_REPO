package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    /** Required only if the agent has an IMEI bound to their account (Branch#requireImei at enrollment time) — see AuthenticationController#login. */
    private String imei;

    /** The mobile app's per-installation id (generated client-side, wiped on uninstall) — distinct from {@link #imei}, the physical device. Nullable so an older app build (or a first-ever login before this shipped) doesn't fail validation; see AuthenticationController#login and AgentInstallationBinding. */
    private String installationId;
}
