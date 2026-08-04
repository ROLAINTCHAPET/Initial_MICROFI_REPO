package com.microfi.mw.controller;

import com.microfi.mw.adapters.dto.ExportAckResult;
import com.microfi.mw.api.CorrelationId;
import com.microfi.mw.api.dto.DailyExportRequest;
import com.microfi.mw.service.CbsIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mw/v1/exports")
@RequiredArgsConstructor
@Tag(name = "Exports", description = "Daily accounting export acknowledgement (internal, called by Core only)")
public class ExportController {

    private final CbsIntegrationService cbsIntegrationService;

    @PostMapping("/daily")
    @Operation(summary = "Acknowledge a daily CBS-compatible export file")
    ExportAckResult daily(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                           @Valid @RequestBody DailyExportRequest request) {
        return cbsIntegrationService.acknowledgeDailyExport(CorrelationId.resolve(correlationId),
                request.branchId(), request.fileUri(), request.format());
    }
}
