package com.microfi.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The authorized app *installation* for an agent — distinct from {@link Agent#getImei()} (the
 * physical device). {@code imei} (Android SSAID) deliberately survives an uninstall/reinstall of
 * the same signed build, which is exactly why it cannot detect the fraud scenario this exists for:
 * an agent wipes the app before syncing offline collections and reinstalls to keep collecting with
 * an empty local history. {@code installationId} is generated client-side and stored in
 * flutter_secure_storage, which — unlike SSAID — is wiped on uninstall, so a reinstall always
 * presents a fresh one.
 *
 * History-preserving by design (append a new row per binding rather than mutating one in place):
 * an admin reset needs to record "previous binding -> new binding" as a pair, which a single
 * mutable column on {@link Agent} can't express. At most one row per agent has
 * {@link #supersededAt} null at a time — that row is the current binding.
 */
@Entity
@Table(name = "agent_installation_binding", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInstallationBinding {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private String installationId;

    @Column(nullable = false)
    private Instant boundAt;

    /** Null while this is the agent's current binding; stamped the moment a newer binding supersedes it (a fresh first-time bind, or an admin reset). */
    private Instant supersededAt;

    /** Per-installation HMAC secret for the collection hash-chain, generated once at bind time, base64. Null until the hash-chain feature is wired up — a fresh binding row is the natural place to generate it. */
    private String hmacSecretBase64;

    /** Stamped the moment this installation completes one ONLINE collection while the agent's status is RESET_AUTHORIZED (or immediately for a never-reset installation, which has nothing to prove). Null means "still owes an online-first collection" — see AgentDirectoryService#requireOnlineFirstCollectionCompletedForOffline. */
    private Instant onlineFirstCollectionCompletedAt;
}
