package com.oms.divisionorders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oms.divisionorders.TestProps;
import com.oms.divisionorders.api.dto.DivisionSummaryResponse;
import com.oms.divisionorders.api.dto.DivisionSummaryResponse.DivisionOrders;
import com.oms.divisionorders.domain.DateRange;
import com.oms.divisionorders.domain.InvalidDateRangeException;
import com.oms.divisionorders.domain.StateOrderCount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DivisionOrderServiceTest {

    // 2026-09-24T02:00Z is still Sep 23 in New York.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T02:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate D1 = LocalDate.of(2026, 9, 22);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 23);

    private final List<StateOrderCount> rows = new ArrayList<>();
    private final List<DateRange> queried = new ArrayList<>();
    private final DivisionOrderService service = new DivisionOrderService(
            range -> { queried.add(range); return rows; }, TestProps.props(), CLOCK);

    @Test
    void defaultWindowIsLastSevenDaysEndingTodayInBusinessTimeZone() {
        DateRange range = service.resolveRange(null, null);
        assertThat(range.end()).isEqualTo(D2);
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(range.days()).isEqualTo(7);
    }

    @Test
    void clampsFutureEndDateAndRejectsBadRanges() {
        assertThat(service.resolveRange(D1, LocalDate.of(2026, 12, 1)).end()).isEqualTo(D2);
        assertThatThrownBy(() -> service.resolveRange(D2, D1)).isInstanceOf(InvalidDateRangeException.class);
        assertThatThrownBy(() -> service.resolveRange(LocalDate.of(2026, 1, 1), D2))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("maximum is 92");
    }

    @Test
    void rollsStatesUpIntoDivisionsWithZeroFilledBreakdowns() {
        rows.add(new StateOrderCount(D1, "CA", 100, null));
        rows.add(new StateOrderCount(D2, "CA", 50, null));
        rows.add(new StateOrderCount(D2, "WA", 30, null));
        rows.add(new StateOrderCount(D1, "NY", 20, null));
        rows.add(new StateOrderCount(D2, "PR", 7, null));
        rows.add(new StateOrderCount(D2, "UNKNOWN", 3, null));

        DivisionSummaryResponse res = service.divisionSummary(new DateRange(D1, D2));

        assertThat(res.totalOrders()).isEqualTo(200);
        assertThat(res.totalDemand()).isNull();
        assertThat(res.divisions()).hasSize(9);
        assertThat(res.divisions().get(0).id()).isEqualTo("PAC");

        DivisionOrders pac = res.divisions().get(0);
        assertThat(pac.orderCount()).isEqualTo(180);
        assertThat(pac.share()).isEqualTo(0.9);
        assertThat(pac.lat()).isBetween(30.0, 50.0);
        assertThat(pac.states()).extracting("state").containsExactly("CA", "WA", "AK", "HI", "OR");
        assertThat(pac.daily()).extracting("orderCount").containsExactly(100L, 80L);

        DivisionOrders ne = res.divisions().stream().filter(d -> d.id().equals("NE")).findFirst().orElseThrow();
        assertThat(ne.orderCount()).isZero();
        assertThat(ne.daily()).hasSize(2).allSatisfy(d -> assertThat(d.orderCount()).isZero());

        assertThat(res.unmapped().orderCount()).isEqualTo(10);
        assertThat(res.unmapped().states()).containsExactly("PR", "UNKNOWN");
    }

    @Test
    void includesDemandWhenTheSourceHasIt() {
        rows.add(new StateOrderCount(D1, "TX", 2, new BigDecimal("210.50")));
        rows.add(new StateOrderCount(D2, "TX", 1, new BigDecimal("99.50")));

        DivisionSummaryResponse res = service.divisionSummary(new DateRange(D1, D2));

        assertThat(res.totalDemand()).isEqualByComparingTo("310.00");
        DivisionOrders wsc = res.divisions().get(0);
        assertThat(wsc.id()).isEqualTo("WSC");
        assertThat(wsc.demand()).isEqualByComparingTo("310.00");
        assertThat(wsc.daily().get(1).demand()).isEqualByComparingTo("99.50");
    }

    @Test
    void ignoresRowsOutsideTheRequestedRange() {
        rows.add(new StateOrderCount(D1.minusDays(5), "CA", 999, null));
        rows.add(new StateOrderCount(D1, "CA", 1, null));

        assertThat(service.divisionSummary(new DateRange(D1, D2)).totalOrders()).isEqualTo(1);
    }
}
