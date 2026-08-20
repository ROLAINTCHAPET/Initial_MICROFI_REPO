package com.microfi.savings.repository;

import com.microfi.savings.domain.ActivationPayment;
import com.microfi.savings.domain.PaymentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivationPaymentRepository extends JpaRepository<ActivationPayment, UUID> {

    List<ActivationPayment> findByTag(PaymentTag tag);

    @Query("SELECT COALESCE(SUM(p.amountXaf), 0) FROM ActivationPayment p "
            + "WHERE p.agentId = :agentId AND p.paidAt >= :start AND p.paidAt < :end")
    long sumAmountByAgentAndWindow(@Param("agentId") UUID agentId, @Param("start") Instant start, @Param("end") Instant end);

    /** UC-16/18: gathers a branch's activation-fee cash for the day so it can be posted to the CBS on export, same as Collection. */
    List<ActivationPayment> findByAgentIdInAndPaidAtBetween(List<UUID> agentIds, Instant start, Instant end);
}
