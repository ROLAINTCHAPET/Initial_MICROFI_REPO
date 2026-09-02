package com.microfi.authentication.service;

import com.microfi.authentication.domain.Terminal;
import com.microfi.authentication.repository.TerminalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalRepository terminalRepository;

    /** Records a successful login from {@code deviceId} — creates the terminal on its first-ever sighting, otherwise just updates who last used it. */
    public void recognize(String deviceId, UUID agentId) {
        Instant now = Instant.now();
        Terminal terminal = terminalRepository.findByDeviceId(deviceId).orElseGet(() -> Terminal.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .firstSeenAt(now)
                .firstSeenByAgentId(agentId)
                .build());
        terminal.setLastSeenAt(now);
        terminal.setLastSeenByAgentId(agentId);
        terminalRepository.save(terminal);
    }
}
