package com.microfi.shared.dto;

import lombok.Data;

@Data
public class ExportRequest {
    /** Defaults to CSV if omitted. Per-tenant format selection (BR-Export-02) is multi-tenant scope, deferred. */
    private String format;
}
