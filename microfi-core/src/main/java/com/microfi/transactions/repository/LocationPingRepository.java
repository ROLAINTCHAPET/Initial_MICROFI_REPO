package com.microfi.transactions.repository;

import com.microfi.transactions.domain.LocationPing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LocationPingRepository extends JpaRepository<LocationPing, UUID> {
}
