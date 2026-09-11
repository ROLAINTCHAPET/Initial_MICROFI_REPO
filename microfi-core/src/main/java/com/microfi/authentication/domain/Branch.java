package com.microfi.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branch", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Branch {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String phone;

    private LocalTime openTime;

    private LocalTime closeTime;

    private String timezone;

    /** Cap on BRANCH_CASHIER accounts for this branch; null means {@link #DEFAULT_MAX_CASHIERS} applies. */
    private Integer maxCashiers;

    public static final int DEFAULT_MAX_CASHIERS = 3;

    public int effectiveMaxCashiers() {
        return maxCashiers != null ? maxCashiers : DEFAULT_MAX_CASHIERS;
    }

    /**
     * Whether enrolling a new agent at this branch must supply an IMEI (FR-01/BR-Auth-02 device
     * binding). Null means {@link #DEFAULT_REQUIRE_IMEI} applies (preserves existing behavior for
     * every branch that predates this setting). Some microfinances let agents use their own
     * phone to collect, in which case there's no institution-owned device to bind to.
     */
    private Boolean requireImei;

    public static final boolean DEFAULT_REQUIRE_IMEI = true;

    public boolean effectiveRequireImei() {
        return requireImei != null ? requireImei : DEFAULT_REQUIRE_IMEI;
    }

    /**
     * The escrow ceiling an agent at this branch is granted per XAF of security deposit funded
     * via top-up (EscrowService#topUp) — e.g. 100 means ceiling rises 1:1 with the deposit
     * (today's behavior, and the default for every branch that predates this setting); 150 would
     * grant a ceiling 1.5x the deposited amount. Doesn't affect a temporary waiver
     * (CeilingOverride), which is still set to whatever the admin/manager enters directly.
     */
    private Integer defaultCeilingPct;

    public static final int DEFAULT_CEILING_PCT = 100;

    public int effectiveDefaultCeilingPct() {
        return defaultCeilingPct != null ? defaultCeilingPct : DEFAULT_CEILING_PCT;
    }

    /**
     * UC-19: whether an agent at this branch may only collect cash from a client who has
     * completed activation (a currently-{@code ACTIVE}, unexpired {@link
     * com.microfi.savings.domain.AccessToken}). Null means {@link
     * #DEFAULT_REQUIRE_CLIENT_ACTIVATION} applies — off by default, preserving today's behavior
     * (an agent can already collect from any client regardless of activation status) for every
     * branch until an admin/branch-manager opts in.
     */
    private Boolean requireClientActivation;

    public static final boolean DEFAULT_REQUIRE_CLIENT_ACTIVATION = false;

    public boolean effectiveRequireClientActivation() {
        return requireClientActivation != null ? requireClientActivation : DEFAULT_REQUIRE_CLIENT_ACTIVATION;
    }

    /**
     * "Portefeuille client" — whether an agent at this branch may only collect cash from a client
     * already assigned to them ({@code ClientProfile#getAssignedAgentId()}). A client gets assigned
     * automatically the first time an agent sponsors their UC-19 activation to completion; a
     * manager/admin can also reassign one by hand. A client with no assigned agent yet (bulk-
     * imported from the CBS, or created directly by an admin/manager) stays open to any agent in
     * the branch regardless of this setting — the restriction only ever narrows an *existing*
     * assignment, it never blocks an otherwise-untouched client. Null means {@link
     * #DEFAULT_REQUIRE_CLIENT_PORTFOLIO} applies — off by default, preserving today's behavior for
     * every branch until an admin/branch-manager opts in.
     */
    private Boolean requireClientPortfolio;

    public static final boolean DEFAULT_REQUIRE_CLIENT_PORTFOLIO = false;

    public boolean effectiveRequireClientPortfolio() {
        return requireClientPortfolio != null ? requireClientPortfolio : DEFAULT_REQUIRE_CLIENT_PORTFOLIO;
    }
}
