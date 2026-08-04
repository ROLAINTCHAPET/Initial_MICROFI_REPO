package com.microfi.mw.repository;

import com.microfi.mw.domain.OutboxJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxJobRepository extends JpaRepository<OutboxJob, UUID> {
}
