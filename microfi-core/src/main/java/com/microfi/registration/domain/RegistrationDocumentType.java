package com.microfi.registration.domain;

/** The five documents a CEMAC-compliant enrollment dossier requires. Doubles as the multipart part name (camelCase) and the download-endpoint path variable. */
public enum RegistrationDocumentType {
    NATIONAL_ID,
    CRIMINAL_RECORD,
    MEDICAL_FITNESS,
    LOCATION_PLAN,
    PASSPORT_PHOTO
}
