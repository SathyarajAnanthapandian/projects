package com.oms.divisionorders.api;

import com.oms.divisionorders.api.dto.DivisionSummaryResponse;
import com.oms.divisionorders.api.dto.DivisionSummaryResponse.DivisionOrders;
import com.oms.divisionorders.api.dto.StateDayRow;
import com.oms.divisionorders.domain.DateRange;
import com.oms.divisionorders.domain.Division;
import com.oms.divisionorders.service.DivisionOrderService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Placed-order counts by US division. Both dates are optional business dates
 * (YYYY-MM-DD, inclusive). Without them the window is the last 7 days including today.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class DivisionOrdersController {

    private final DivisionOrderService service;

    public DivisionOrdersController(DivisionOrderService service) {
        this.service = service;
    }

    /** Every division, sorted by order count, with marker coordinates and breakdowns. */
    @GetMapping("/divisions")
    public DivisionSummaryResponse divisions(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return service.divisionSummary(service.resolveRange(startDate, endDate));
    }

    /** A single division, for example {@code /divisions/PAC}. */
    @GetMapping("/divisions/{divisionId}")
    public DivisionOrders division(
            @PathVariable String divisionId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        Division division = Division.forId(divisionId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Unknown division '" + divisionId + "'"));
        DateRange range = service.resolveRange(startDate, endDate);
        return service.divisionSummary(range).divisions().stream()
                .filter(d -> d.id().equals(division.name()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Raw rows, one per business date and ship-to state, in the shape the
     * division map page reads (division-orders-map/data.js).
     */
    @GetMapping("/by-state")
    public List<StateDayRow> byState(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return service.stateRows(service.resolveRange(startDate, endDate)).stream()
                .map(r -> new StateDayRow(r.date(), r.state(), r.orders(), r.demand()))
                .toList();
    }
}
