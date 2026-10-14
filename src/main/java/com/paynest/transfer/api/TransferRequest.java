package com.paynest.transfer.api;

import java.math.BigDecimal;

public record TransferRequest(String recipientEmail, BigDecimal amount) {
}
