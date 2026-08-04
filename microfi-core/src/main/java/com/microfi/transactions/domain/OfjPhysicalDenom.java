package com.microfi.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Cashier-counted physical denomination breakdown backing an {@link OfjAgentLine}. */
@Entity
@Table(name = "ofj_physical_denom", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfjPhysicalDenom {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ofjAgentLineId;

    @Column(nullable = false)
    private long faceValueXaf;

    @Column(nullable = false)
    private int quantity;
}
