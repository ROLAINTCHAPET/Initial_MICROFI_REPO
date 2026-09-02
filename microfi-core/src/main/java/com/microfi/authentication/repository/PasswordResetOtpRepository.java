package com.microfi.authentication.repository;

import com.microfi.authentication.domain.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, UUID> {
    Optional<PasswordResetOtp> findTopByAgentIdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID agentId);
}
