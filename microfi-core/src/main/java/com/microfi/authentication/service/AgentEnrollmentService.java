package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.shared.dto.RegisterRequest;
import com.microfi.transactions.service.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The agent-creation business logic, extracted verbatim from
 * {@code AgentManagementController#register} so {@code RegistrationApplicationService} can
 * provision an agent from an approved {@code RegistrationApplication} through the exact same
 * path — uniqueness checks, escrow account creation, {@code PENDING_CEILING} start state — rather
 * than a second, divergent implementation. Blocking (JPA), same as the code it was extracted
 * from; callers wrap it in {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}.
 */
@Service
@RequiredArgsConstructor
public class AgentEnrollmentService {

    private final AgentRepository agentRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final EscrowService escrowService;

    public Agent enroll(RegisterRequest request) {
        if (agentRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username '" + request.getUsername() + "' already exists");
        }
        if (agentRepository.existsByPhone(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phone '" + request.getPhone() + "' is already registered to another agent");
        }
        if (agentRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email '" + request.getEmail() + "' is already registered to another agent");
        }
        // HR/business reference is optional on the form; defaults to the username,
        // which is already guaranteed unique above.
        String employeeCode = request.getEmployeeCode() != null && !request.getEmployeeCode().isBlank()
                ? request.getEmployeeCode()
                : request.getUsername();
        if (agentRepository.existsByEmployeeCode(employeeCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Agent with code '" + employeeCode + "' already exists");
        }
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Branch not found: " + request.getBranchId()));

        Agent agent = Agent.builder()
                .id(UUID.randomUUID())
                .employeeCode(employeeCode)
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .imei(null)
                .branchId(request.getBranchId())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .pinHash(passwordEncoder.encode(request.getPin()))
                .pinMustChange(true)
                .status(AgentStatus.PENDING_CEILING)
                .build();

        Agent saved = agentRepository.save(agent);
        escrowService.createAccountForAgent(saved.getId());
        return saved;
    }

    /**
     * Read-only availability checks, so a caller (e.g. {@code RegistrationApplicationService})
     * can reject a duplicate at submission time rather than only discovering the conflict when
     * {@link #enroll} itself is finally called at approval time.
     */
    public boolean isUsernameTaken(String username) {
        return agentRepository.existsByUsername(username);
    }

    public boolean isPhoneTaken(String phone) {
        return agentRepository.existsByPhone(phone);
    }

    public boolean isEmailTaken(String email) {
        return agentRepository.existsByEmail(email);
    }
}
