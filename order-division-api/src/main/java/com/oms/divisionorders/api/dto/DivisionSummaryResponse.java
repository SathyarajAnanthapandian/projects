package com.oms.divisionorders.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Order counts for every division over a date range. */
public record DivisionSummaryResponse(
        LocalDate startDate,
        LocalDate endDate,
        String timeZone,
        long totalOrders,
        BigDecimal totalDemand,
        List<DivisionOrders> divisions,
        Unmapped unmapped,
        Instant generatedAt) {

    /**
     * One division, ready to draw as a cluster marker ({@code lat}/{@code lng},
     * sized by {@code orderCount}), with state and daily breakdowns for drill-down.
     */
    public record DivisionOrders(
            String id,
            String name,
            double lat,
            double lng,
            long orderCount,
            BigDecimal demand,
            /** Fraction of {@code totalOrders}, 0..1. */
            double share,
            List<StateCount> states,
            List<DailyCount> daily) {}

    public record StateCount(String state, long orderCount, BigDecimal demand) {}

    public record DailyCount(LocalDate date, long orderCount, BigDecimal demand) {}

    /** Orders whose ship-to state isn't in any division: PR, GU, APO/FPO, missing, etc. */
    public record Unmapped(long orderCount, List<String> states) {}
}
