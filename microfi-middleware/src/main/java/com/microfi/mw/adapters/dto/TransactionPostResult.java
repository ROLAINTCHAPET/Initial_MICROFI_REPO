package com.microfi.mw.adapters.dto;

import java.util.List;

public record TransactionPostResult(
    boolean success,
    List<String> postedReferences
) {
}
