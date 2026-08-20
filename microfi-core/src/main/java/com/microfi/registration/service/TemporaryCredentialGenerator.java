package com.microfi.registration.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates the one-time activation credential sent by SMS on approval (see
 * {@code RegistrationApplicationService#approve}). Deliberately never persisted anywhere — the
 * generated value only ever exists in memory long enough to hash it (via the same
 * {@code PasswordEncoder} {@code AgentEnrollmentService}/{@code AdminUserEnrollmentService} already
 * use) and to be returned once in the approval response / composed into the SMS text.
 */
@Component
public class TemporaryCredentialGenerator {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final int PIN_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    public String generatePin() {
        StringBuilder sb = new StringBuilder(PIN_LENGTH);
        for (int i = 0; i < PIN_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
