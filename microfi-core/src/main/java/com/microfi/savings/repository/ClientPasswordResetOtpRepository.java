package com.microfi.savings.repository;

import com.microfi.savings.domain.ClientPasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientPasswordResetOtpRepository extends JpaRepository<ClientPasswordResetOtp, UUID> {
    Optional<ClientPasswordResetOtp> findTopByClientIdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID clientId);
}
