package com.microfi.transactions.repository;

import com.microfi.transactions.domain.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {

    boolean existsByMfiMemberNo(String mfiMemberNo);

    /** FR-06: multi-method lookup by membership number, phone or name (case-insensitive, partial match). */
    @Query("SELECT c FROM ClientProfile c WHERE "
            + "LOWER(c.mfiMemberNo) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ClientProfile> search(@Param("query") String query);
}
