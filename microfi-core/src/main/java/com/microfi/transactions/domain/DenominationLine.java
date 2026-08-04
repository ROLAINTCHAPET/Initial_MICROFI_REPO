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

/** FR-08: physical note/coin breakdown of a collection (500, 1k, 2k, 5k, 10k XAF + coins aggregate). */
@Entity
@Table(name = "denomination_line", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DenominationLine {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID collectionId;

    @Column(nullable = false)
    private long faceValueXaf;

    @Column(nullable = false)
    private int quantity;
}
