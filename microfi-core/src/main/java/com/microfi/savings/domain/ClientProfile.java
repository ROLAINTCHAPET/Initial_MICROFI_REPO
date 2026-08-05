package com.microfi.savings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Local mirror of a CBS member; MICROFI never creates new customer records in the CBS. Owned by
 * {@code savings} (architecture.txt groups {@code client_profile} under the "Client Digital
 * Booklet (FR-19-FR-22)" schema section, not the collections/ledger one) even though
 * {@code transactions} needs to resolve a client mid-collection (UC-06) — that read goes through
 * {@link com.microfi.savings.service.ClientDirectoryService}, this module's public contract.
 * <p>
 * {@code login}/{@code pinHash} are null until UC-19 self-activation sets them (FR-19); a null
 * {@code pinHash} means the client cannot log in yet, independent of {@code status}, which tracks
 * whether the local mirror row itself is usable.
 */
@Entity
@Table(name = "client_profile", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfile {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String mfiMemberNo;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    private UUID branchId;

    private String cbsRef;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(unique = true)
    private String login;

    private String pinHash;
}
