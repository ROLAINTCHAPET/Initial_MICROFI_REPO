package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    /** Contact number field agents can reach in-app from "Contact Branch" — optional, not every branch has one on file yet. */
    private String phone;

    @NotBlank(message = "IANA timezone id is required, e.g. Africa/Douala")
    private String timezone;
}
