package com.microfi.shared.dto;

import lombok.Data;

/** UC-09: {@code printedReceipt} records whether the agent's app also printed a Bluetooth thermal receipt (device-local action this service only logs). */
@Data
public class NotifyCollectionRequest {

    private boolean printedReceipt;
}
