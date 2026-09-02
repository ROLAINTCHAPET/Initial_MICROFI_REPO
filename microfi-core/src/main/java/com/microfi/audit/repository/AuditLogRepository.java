package com.microfi.audit.repository;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // A single flexible query rather than one derived-name method per filter combination — every
    // parameter is optional except the date range (search() always supplies one, see AuditService),
    // and the branch/category/actorType checks fall through to "match anything" when null. No
    // pagination: the mandatory date range already bounds this to a manageable size, same as
    // every other "list" endpoint in this codebase (OFJ history, variance debts, etc.).
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.occurredAt >= :from AND a.occurredAt < :to
            AND (:branchId IS NULL OR a.branchId = :branchId)
            AND (:category IS NULL OR a.category = :category)
            AND (:actorType IS NULL OR a.actorType = :actorType)
            ORDER BY a.occurredAt DESC
            """)
    List<AuditLog> search(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("branchId") UUID branchId,
            @Param("category") AuditCategory category,
            @Param("actorType") AuditActorType actorType);
}
