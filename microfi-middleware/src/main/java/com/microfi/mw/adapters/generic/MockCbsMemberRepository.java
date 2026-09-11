package com.microfi.mw.adapters.generic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MockCbsMemberRepository extends JpaRepository<MockCbsMember, UUID> {

    Optional<MockCbsMember> findByAccountNumber(String accountNumber);
}
