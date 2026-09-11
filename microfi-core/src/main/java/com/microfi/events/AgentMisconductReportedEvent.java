package com.microfi.events;

import com.microfi.shared.dto.AgentMisconductReportResponse;

/** Carries a just-submitted misconduct report to {@link AgentMisconductReportAlertEventRelay} — mirrors {@link SosRaisedEvent}'s placement/timing exactly. */
public record AgentMisconductReportedEvent(
    AgentMisconductReportResponse response
) {
}
