package com.microfi.savings.repository;

import com.microfi.savings.domain.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {

    boolean existsByMfiMemberNo(String mfiMemberNo);

    Optional<ClientProfile> findByCbsRef(String cbsRef);

    Optional<ClientProfile> findByLogin(String login);

    List<ClientProfile> findByBranchId(UUID branchId);

    /**
     * FR-06: multi-method lookup by membership number, phone or name (case-insensitive, partial
     * match), across every client. Not branch-scoped — which clients an agent may actually serve
     * is governed by their assigned geofence at collection time (CollectionService's geofence
     * gate), not by branch membership, since a branch is an org unit, not a coverage area.
     */
    @Query("SELECT c FROM ClientProfile c WHERE "
            + "LOWER(c.mfiMemberNo) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ClientProfile> search(@Param("query") String query);

    /**
     * UC-19 step 2 candidates: the client has already self-activated (set their own login —
     * step 1) but has no live booklet token yet, so the two-party gate is still open. Same
     * name/phone/member-number search as {@link #search}, additionally gated on those two
     * conditions rather than a separate {@code ClientStatus} — that enum tracks whether the local
     * mirror row itself is usable, not booklet-activation progress (see ClientProfile's own doc).
     */
    @Query("SELECT c FROM ClientProfile c WHERE c.status = com.microfi.savings.domain.ClientStatus.ACTIVE "
            + "AND c.login IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM AccessToken t WHERE t.clientId = c.id AND t.status = com.microfi.savings.domain.AccessTokenStatus.ACTIVE) "
            + "AND (:query = '' OR LOWER(c.mfiMemberNo) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<ClientProfile> findPendingActivation(@Param("query") String query);
}
