package com.microfi.mw.repository;

import com.microfi.mw.domain.CbsCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CbsCallLogRepository extends JpaRepository<CbsCallLog, UUID> {
}
