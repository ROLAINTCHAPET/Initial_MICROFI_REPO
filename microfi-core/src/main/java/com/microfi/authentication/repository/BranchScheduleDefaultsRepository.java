package com.microfi.authentication.repository;

import com.microfi.authentication.domain.BranchScheduleDefaults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BranchScheduleDefaultsRepository extends JpaRepository<BranchScheduleDefaults, UUID> {
}
