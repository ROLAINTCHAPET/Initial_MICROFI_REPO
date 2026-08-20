package com.microfi.shared.dto;

import com.microfi.savings.domain.ClientStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateClientStatusRequest {

    @NotNull
    private ClientStatus status;
}
