package com.microfi.shared.dto;

import lombok.Data;

/**
 * UC-09: {@code printedReceipt} records whether the agent's app also printed a Bluetooth thermal
 * receipt (device-local action this service only logs). {@code locale} is the agent's currently
 * selected app language ("en"/"fr") — controls which language the returned {@code receiptText}/
 * {@code receiptData} are composed in, mirroring the mobile app's own offline receipt composer so
 * an online and an offline receipt for the same agent look identical. Null/anything other than
 * "fr" falls back to English, preserving the response shape existing callers already expect.
 */
@Data
public class NotifyCollectionRequest {

    private boolean printedReceipt;
    private String locale;
}
