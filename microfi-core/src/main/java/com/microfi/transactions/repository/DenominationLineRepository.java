package com.microfi.transactions.repository;

import com.microfi.transactions.domain.DenominationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DenominationLineRepository extends JpaRepository<DenominationLine, UUID> {
    List<DenominationLine> findByCollectionId(UUID collectionId);
}
