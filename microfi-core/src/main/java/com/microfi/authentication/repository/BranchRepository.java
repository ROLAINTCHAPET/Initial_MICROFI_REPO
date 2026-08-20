package com.microfi.authentication.repository;

import com.microfi.authentication.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    boolean existsByCode(String code);

    /**
     * Stable ordering for the Back-Office branch list/selector — plain {@code findAll()} has no
     * guaranteed order across calls, which let a page relying on "the first branch returned"
     * (e.g. the admin's Branch Settings default) silently jump to a different branch after a
     * refresh.
     */
    List<Branch> findAllByOrderByNameAsc();
}
