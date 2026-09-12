package com.microfi.authentication.service;

import com.microfi.authentication.domain.AgentInstallationBinding;
import com.microfi.authentication.repository.AgentInstallationBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the {@code AgentInstallationBinding} lifecycle — the "installation" half of the identity
 * model (see the entity's own doc comment for why this is distinct from {@code Agent#imei}, the
 * "device" half). Lives in {@code authentication} alongside {@code Agent} per CLAUDE.md's
 * "modules never touch another module's repository" rule; other modules reach this through
 * {@code AgentDirectoryService}, the existing cross-module facade.
 */
@Service
@RequiredArgsConstructor
public class InstallationBindingService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AgentInstallationBindingRepository repository;

    public Optional<AgentInstallationBinding> findCurrent(UUID agentId) {
        return repository.findFirstByAgentIdAndSupersededAtIsNull(agentId);
    }

    /**
     * Called only when {@link #findCurrent} is empty (first-ever login, or the agent's first
     * login after an admin reset superseded their prior binding) — records the presented
     * installation id as the new current binding and generates its HMAC secret for the collection
     * hash-chain (transmitted to the mobile app exactly once, in this login's response — see
     * AuthenticationController#login — never re-sent afterward).
     *
     * @param requireOnlineFirstCollection true only when this bind follows an admin's device-
     *        binding reset (agent status RESET_AUTHORIZED at bind time) — the spec's "newly
     *        authorized installation repeats the required online onboarding and first-collection
     *        procedure" rule. A genuinely first-ever binding (an agent who has never been reset)
     *        has nothing to prove and is stamped complete immediately.
     */
    public AgentInstallationBinding bindFirst(UUID agentId, String installationId, boolean requireOnlineFirstCollection) {
        byte[] secretBytes = new byte[32];
        SECURE_RANDOM.nextBytes(secretBytes);
        AgentInstallationBinding binding = AgentInstallationBinding.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .installationId(installationId)
                .boundAt(Instant.now())
                .hmacSecretBase64(Base64.getEncoder().encodeToString(secretBytes))
                .onlineFirstCollectionCompletedAt(requireOnlineFirstCollection ? null : Instant.now())
                .build();
        return repository.save(binding);
    }

    /** Admin-reset path (see AgentManagementController#resetDeviceBinding): closes out the current binding without creating a new one — the next login records a fresh one via {@link #bindFirst}, exactly like device-binding reset works today for {@code imei}. Returns the superseded row (for capturing its installationId in the reset's audit entry), or empty if the agent had no current binding to begin with. */
    public Optional<AgentInstallationBinding> supersedeCurrent(UUID agentId) {
        Optional<AgentInstallationBinding> current = findCurrent(agentId);
        current.ifPresent(binding -> {
            binding.setSupersededAt(Instant.now());
            repository.save(binding);
        });
        return current;
    }

    /** See AgentDirectoryService#completeOnlineFirstCollectionIfNeeded. */
    public void markOnlineFirstCollectionCompleted(AgentInstallationBinding binding) {
        binding.setOnlineFirstCollectionCompletedAt(Instant.now());
        repository.save(binding);
    }
}
