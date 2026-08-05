package com.microfi.savings.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record for cash an agent has physically collected from a client (architecture.txt
 * core.activation_payment, FR-19). Not a CBS debit — the client hands the agent cash in person,
 * the same way any {@link com.microfi.transactions.domain.Collection} works, so it counts against
 * the agent's escrow ceiling (see {@code CollectionService.enforceEscrowCeiling}). {@code tag}
 * distinguishes what the cash was for; only {@code ACTIVATION} exists today but the field is
 * ready for other agent-collected payment types later.
 */
@Entity
@Table(name = "activation_payment", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationPayment {

    @Id
    private UUID id;

    private UUID clientId;

    private UUID agentId;

    private long amountXaf;

    private long agentCommissionXaf;

    private long mfiShareXaf;

    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentTag tag = PaymentTag.ACTIVATION;
}
