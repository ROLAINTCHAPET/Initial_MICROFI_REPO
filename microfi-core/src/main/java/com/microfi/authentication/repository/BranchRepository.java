package com.microfi.authentication.repository;

import com.microfi.authentication.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    boolean existsByCode(String code);
}
