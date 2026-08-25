package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.shared.dto.ChangeAgentPinRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Business logic behind AgentSelfController's own-profile actions — currently just the transaction-PIN change. */
@Service
@RequiredArgsConstructor
public class AgentSelfService {

    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;

    /** Verifies the current PIN, rejects a weak replacement, then persists the change and clears any lockout. */
    public Agent changePin(Agent agent, ChangeAgentPinRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPin(), agent.getPinHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current PIN is incorrect");
        }
        if (isWeakPin(request.getNewPin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PIN must not be all the same digit or a simple sequence (e.g. 1234)");
        }
        agent.setPinHash(passwordEncoder.encode(request.getNewPin()));
        agent.setPinMustChange(false);
        agent.setFailedTransactionPinAttempts(0);
        agent.setTransactionPinLockedUntil(null);
        return agentRepository.save(agent);
    }

    /** Rejects the two weakest numeric-PIN shapes: every digit the same, or a simple ascending/descending run. Only meaningful for purely numeric PINs — non-numeric values pass through untouched. */
    private boolean isWeakPin(String pin) {
        if (!pin.chars().allMatch(Character::isDigit)) {
            return false;
        }
        boolean allSame = pin.chars().distinct().count() == 1;
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) != pin.charAt(i - 1) + 1) {
                ascending = false;
            }
            if (pin.charAt(i) != pin.charAt(i - 1) - 1) {
                descending = false;
            }
        }
        return allSame || ascending || descending;
    }
}
