package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollPaymentRegisteredEvent(
        UUID eventId,
        UUID clinicId,
        UUID payrollPeriodId,
        UUID bankAccountId,
        String fundingAccountCode,
        String fundingAccountName,
        BigDecimal grossAmount,
        BigDecimal netAmount,
        BigDecimal deductionAmount,
        LocalDate paymentDate
) {}
