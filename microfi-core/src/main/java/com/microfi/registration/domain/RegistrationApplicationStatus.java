package com.microfi.registration.domain;

/**
 * {@code SUBMITTED} doubles as the "pending compliance review" state — there's no separate draft
 * stage, a wizard submission is already complete. {@code APPROVED} is only ever persisted once
 * the real {@code Agent}/{@code AdminUser} has actually been provisioned (see
 * {@code RegistrationApplicationService#approve}), so it also doubles as "provisioned" without a
 * fourth state.
 */
public enum RegistrationApplicationStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
