package com.expensetracker.dto;

import java.math.BigDecimal;
import java.util.Map;

public record SummaryResponse(
        BigDecimal total,
        long count,
        BigDecimal average,
        Map<String, BigDecimal> byCategory,
        Map<String, BigDecimal> byMonth) {
}
