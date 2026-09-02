package com.microfi.authentication.repository;

import com.microfi.authentication.domain.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, UUID> {
    Optional<Terminal> findByDeviceId(String deviceId);
}
