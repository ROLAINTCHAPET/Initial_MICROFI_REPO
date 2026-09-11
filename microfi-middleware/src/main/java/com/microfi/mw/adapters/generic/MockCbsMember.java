package com.microfi.mw.adapters.generic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * {@link MockCbsAdapter}'s simulated CBS member directory — deliberately not part of the shared
 * {@code com.microfi.mw.domain} package, same reasoning as {@link MockLedgerEntry}: a real vendor
 * adapter (Amplitude, FinanSoft) already has its own member registry, so this only exists to give
 * the mock something real to look member-by-account-number lookups up against, instead of
 * fabricating a plausible-looking result with no data behind it. Rows here represent a member
 * that "already exists in the CBS," independent of anything in MICROFI's own {@code
 * core.client_profile} — seeded via {@code POST /mw/v1/members} for dev/test use, since a real
 * CBS is the one place MICROFI itself never writes.
 */
@Entity
@Table(name = "mock_cbs_member", schema = "mw")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockCbsMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private String fullName;

    private String email;

    private String phone;
}
