package com.microfi.transactions.repository;

import com.microfi.transactions.domain.ExportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExportBatchRepository extends JpaRepository<ExportBatch, UUID> {
    /** A session can now have more than one batch (export is repeatable/idempotent) — see ExportBatch's doc. */
    List<ExportBatch> findByOfjId(UUID ofjId);

    boolean existsByOfjId(UUID ofjId);
}
