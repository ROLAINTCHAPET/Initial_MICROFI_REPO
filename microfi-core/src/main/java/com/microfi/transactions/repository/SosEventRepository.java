package com.microfi.transactions.repository;

import com.microfi.transactions.domain.SosEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SosEventRepository extends JpaRepository<SosEvent, UUID> {

    List<SosEvent> findAllByOrderByRaisedAtDesc();

    List<SosEvent> findByAgentIdInOrderByRaisedAtDesc(List<UUID> agentIds);

    List<SosEvent> findByAcknowledgedAtIsNullOrderByRaisedAtDesc();

    List<SosEvent> findByAgentIdInAndAcknowledgedAtIsNullOrderByRaisedAtDesc(List<UUID> agentIds);
}
