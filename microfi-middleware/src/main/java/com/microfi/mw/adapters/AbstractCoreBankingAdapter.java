package com.microfi.mw.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Common scaffolding for {@link CoreBankingAdapter} implementations, so a new vendor adapter
 * (Amplitude, FinanSoft, ...) only has to implement the CBS-specific operations — vendor-key
 * wiring, logging and correlation-reference generation are inherited rather than re-derived by
 * every adapter. Concrete adapters register themselves as Spring beans as before; nothing about
 * {@link CbsAdapterFactory}'s vendor lookup changes.
 */
public abstract class AbstractCoreBankingAdapter implements CoreBankingAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final String vendor() {
        return vendorKey();
    }

    /** Unique vendor key this adapter implements, matched against the {@code cbs.vendor} property. */
    protected abstract String vendorKey();

    /** Generates a prefixed correlation reference (e.g. {@code CBSTX-<uuid>}) for operations that need a client-side one. */
    protected static String newReference(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
