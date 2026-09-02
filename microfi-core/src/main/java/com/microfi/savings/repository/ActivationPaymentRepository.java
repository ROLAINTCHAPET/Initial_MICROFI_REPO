package com.microfi.savings.repository;

import com.microfi.savings.domain.ActivationPayment;
import com.microfi.savings.domain.PaymentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivationPaymentRepository extends JpaRepository<ActivationPayment, UUID> {

    List<ActivationPayment> findByTag(PaymentTag tag);

    /** UC-16 / BR-03: same reconciliation-sweep semantics as CollectionRepository#sumUnreconciledByAgent — see that Javadoc. */
    @Query("SELECT COALESCE(SUM(p.amountXaf), 0) FROM ActivationPayment p "
            + "WHERE p.agentId = :agentId AND p.reconciledAt IS NULL AND p.paidAt < :cutoff")
    long sumUnreconciledByAgent(@Param("agentId") UUID agentId, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE ActivationPayment p SET p.reconciledAt = :cutoff, p.reconciledInLineId = :lineId "
            + "WHERE p.agentId = :agentId AND p.reconciledAt IS NULL AND p.paidAt < :cutoff")
    int markReconciled(@Param("agentId") UUID agentId, @Param("cutoff") Instant cutoff, @Param("lineId") UUID lineId);

    /** UC-16/18: exactly the activation payments a given set of OfjAgentLines reconciled, for CBS export. */
    List<ActivationPayment> findByReconciledInLineIdIn(List<UUID> lineIds);

    /** UC-16/18: gathers a branch's activation-fee cash for the day so it can be posted to the CBS on export, same as Collection. */
    List<ActivationPayment> findByAgentIdInAndPaidAtBetween(List<UUID> agentIds, Instant start, Instant end);
}
