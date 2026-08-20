package com.microfi.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Single-row, admin-configurable org identity used in notification content (BR-Notif-01's
 * mandatory "MFI name" mention on the SMS/receipt text) — a fixed row rather than a general
 * key-value settings table since this is the only admin-configurable setting so far; not
 * over-building a settings framework nothing else needs yet.
 */
@Entity
@Table(name = "mfi_settings", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfiSettings {

    /** Always the same id — this table only ever has one row. */
    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;
}
