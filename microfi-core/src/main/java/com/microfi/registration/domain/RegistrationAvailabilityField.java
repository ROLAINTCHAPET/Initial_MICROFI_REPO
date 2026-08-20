package com.microfi.registration.domain;

/** Which uniqueness-checked field a live availability lookup (GET .../availability) is asking about. */
public enum RegistrationAvailabilityField {
    LOGIN,
    PHONE,
    EMAIL,
    NATIONAL_ID,
    TAX_ID
}
