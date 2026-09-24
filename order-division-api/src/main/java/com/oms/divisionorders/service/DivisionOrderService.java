package com.oms.divisionorders.service;

import com.oms.divisionorders.api.dto.DivisionSummaryResponse;
import com.oms.divisionorders.api.dto.DivisionSummaryResponse.DailyCount;
import com.oms.divisionorders.api.dto.DivisionSummaryResponse.DivisionOrders;
import com.oms.divisionorders.api.dto.DivisionSummaryResponse.StateCount;
import com.oms.divisionorders.api.dto.DivisionSummaryResponse.Unmapped;
import com.oms.divisionorders.config.OrderEventsProperties;
import com.oms.divisionorders.domain.DateRange;
import com.oms.divisionorders.domain.Division;
import com.oms.divisionorders.domain.InvalidDateRangeException;
import com.oms.divisionorders.domain.StateOrderCount;
import com.oms.divisionorders.repository.OrderEventsRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class DivisionOrderService {

    private final OrderEventsRepository repository;
    private final OrderEventsProperties props;
    private final Clock clock;

    public DivisionOrderService(OrderEventsRepository repository, OrderEventsProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Fills in missing dates. With neither date given, the window is the last
     * {@code defaultDays} days ending today, where today is the current date in
     * the configured time zone.
     */
    public DateRange resolveRange(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now(clock.withZone(props.timeZone()));
        LocalDate resolvedEnd = end != null ? end : (start != null ? start.plusDays(props.defaultDays() - 1L) : today);
        if (resolvedEnd.isAfter(today)) {
            resolvedEnd = today;
        }
        LocalDate resolvedStart = start != null ? start : resolvedEnd.minusDays(props.defaultDays() - 1L);
        DateRange range = new DateRange(resolvedStart, resolvedEnd);
        if (range.days() > props.maxRangeDays()) {
            throw new InvalidDateRangeException(
                    "Date range is " + range.days() + " days; the maximum is " + props.maxRangeDays());
        }
        return range;
    }

    public List<StateOrderCount> stateRows(DateRange range) {
        return repository.countPlacedOrdersByStateAndDay(range);
    }

    public DivisionSummaryResponse divisionSummary(DateRange range) {
        List<StateOrderCount> rows = repository.countPlacedOrdersByStateAndDay(range);

        Map<Division, Acc> byDivision = new EnumMap<>(Division.class);
        for (Division d : Division.values()) {
            byDivision.put(d, new Acc(d, range.dates()));
        }
        long unmappedOrders = 0;
        TreeSet<String> unmappedStates = new TreeSet<>();
        boolean hasDemand = false;

        for (StateOrderCount r : rows) {
            hasDemand |= r.demand() != null;
            var division = Division.forState(r.state());
            if (division.isEmpty()) {
                unmappedOrders += r.orders();
                unmappedStates.add(r.state());
                continue;
            }
            byDivision.get(division.get()).add(r);
        }

        long total = byDivision.values().stream().mapToLong(a -> a.orders).sum();
        BigDecimal totalDemand = hasDemand
                ? byDivision.values().stream().map(a -> a.demand).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;

        boolean withDemand = hasDemand;
        List<DivisionOrders> divisions = byDivision.values().stream()
                .map(a -> a.toDto(total, withDemand))
                .sorted(Comparator.comparingLong(DivisionOrders::orderCount).reversed())
                .toList();

        return new DivisionSummaryResponse(
                range.start(), range.end(), props.timeZone().getId(),
                total, totalDemand, divisions,
                new Unmapped(unmappedOrders, List.copyOf(unmappedStates)),
                clock.instant());
    }

    /** Running totals for one division. */
    private static final class Acc {
        final Division division;
        long orders;
        BigDecimal demand = BigDecimal.ZERO;
        final Map<String, long[]> stateOrders = new TreeMap<>();
        final Map<String, BigDecimal> stateDemand = new TreeMap<>();
        final Map<LocalDate, long[]> dayOrders = new LinkedHashMap<>();
        final Map<LocalDate, BigDecimal> dayDemand = new LinkedHashMap<>();

        Acc(Division division, List<LocalDate> dates) {
            this.division = division;
            // Every day and every state appear in the output, with zeros where nothing
            // was ordered, so the client never has to fill gaps.
            for (LocalDate d : dates) {
                dayOrders.put(d, new long[1]);
                dayDemand.put(d, BigDecimal.ZERO);
            }
            for (String s : division.states()) {
                stateOrders.put(s, new long[1]);
                stateDemand.put(s, BigDecimal.ZERO);
            }
        }

        void add(StateOrderCount r) {
            long[] day = dayOrders.get(r.date());
            if (day == null) {
                return; // outside the requested range
            }
            BigDecimal d = r.demand() == null ? BigDecimal.ZERO : r.demand();
            orders += r.orders();
            demand = demand.add(d);
            day[0] += r.orders();
            dayDemand.merge(r.date(), d, BigDecimal::add);
            stateOrders.get(r.state())[0] += r.orders();
            stateDemand.merge(r.state(), d, BigDecimal::add);
        }

        DivisionOrders toDto(long total, boolean withDemand) {
            List<StateCount> states = new ArrayList<>();
            stateOrders.forEach((s, n) -> states.add(new StateCount(s, n[0], withDemand ? stateDemand.get(s) : null)));
            states.sort(Comparator.comparingLong(StateCount::orderCount).reversed());
            List<DailyCount> daily = new ArrayList<>();
            dayOrders.forEach((d, n) -> daily.add(new DailyCount(d, n[0], withDemand ? dayDemand.get(d) : null)));
            return new DivisionOrders(
                    division.name(), division.displayName(), division.lat(), division.lng(),
                    orders, withDemand ? demand : null,
                    total == 0 ? 0 : (double) orders / total,
                    states, daily);
        }
    }
}
