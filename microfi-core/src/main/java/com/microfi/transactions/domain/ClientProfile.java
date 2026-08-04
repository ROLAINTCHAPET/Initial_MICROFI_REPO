package com.microfi.transactions.domain;

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

/** Local mirror of a CBS member; MICROFI never creates new customer records in the CBS. */
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
}
