package com.microfi.savings.repository;

import com.microfi.savings.domain.AccessToken;
import com.microfi.savings.domain.AccessTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

    List<AccessToken> findByClientIdAndStatus(UUID clientId, AccessTokenStatus status);

    /** Most recent non-revoked token for a client, if any — used to derive booklet activation state. */
    Optional<AccessToken> findFirstByClientIdAndStatusOrderByIssuedAtDesc(UUID clientId, AccessTokenStatus status);
}
