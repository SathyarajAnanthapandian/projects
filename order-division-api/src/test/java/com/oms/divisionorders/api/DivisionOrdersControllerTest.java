package com.oms.divisionorders.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.oms.divisionorders.domain.DateRange;
import com.oms.divisionorders.domain.StateOrderCount;
import com.oms.divisionorders.repository.OrderEventsRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Full application context with BigQuery replaced by a mock repository. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "oms.order-events.project-id=oms-prod",
        "oms.order-events.dataset=oms_events",
})
class DivisionOrdersControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired MockMvc mvc;
    @MockBean BigQuery bigQuery;
    @MockBean OrderEventsRepository repository;

    @Test
    void divisionsReturnsClusterReadySummary() throws Exception {
        when(repository.countPlacedOrdersByStateAndDay(any())).thenReturn(List.of(
                new StateOrderCount(DAY, "CA", 40, null),
                new StateOrderCount(DAY, "FL", 60, null)));

        mvc.perform(get("/api/v1/orders/divisions").param("startDate", "2026-09-20").param("endDate", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-09-20"))
                .andExpect(jsonPath("$.timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.totalOrders").value(100))
                .andExpect(jsonPath("$.divisions.length()").value(9))
                .andExpect(jsonPath("$.divisions[0].id").value("SA"))
                .andExpect(jsonPath("$.divisions[0].name").value("South Atlantic"))
                .andExpect(jsonPath("$.divisions[0].orderCount").value(60))
                .andExpect(jsonPath("$.divisions[0].share").value(0.6))
                .andExpect(jsonPath("$.divisions[0].lat").isNumber())
                .andExpect(jsonPath("$.divisions[0].daily.length()").value(2))
                .andExpect(jsonPath("$.divisions[0].daily[1].date").value("2026-09-21"));

        verify(repository).countPlacedOrdersByStateAndDay(
                new DateRange(LocalDate.of(2026, 9, 20), DAY));
    }

    @Test
    void singleDivisionIsCaseInsensitiveAnd404sWhenUnknown() throws Exception {
        when(repository.countPlacedOrdersByStateAndDay(any())).thenReturn(List.of(
                new StateOrderCount(DAY, "OR", 5, null)));

        mvc.perform(get("/api/v1/orders/divisions/pac").param("startDate", "2026-09-21").param("endDate", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PAC"))
                .andExpect(jsonPath("$.orderCount").value(5));

        mvc.perform(get("/api/v1/orders/divisions/XYZ")).andExpect(status().isNotFound());
    }

    @Test
    void byStateMatchesTheMapPageContract() throws Exception {
        when(repository.countPlacedOrdersByStateAndDay(any())).thenReturn(List.of(
                new StateOrderCount(DAY, "TX", 12, null)));

        mvc.perform(get("/api/v1/orders/by-state").param("startDate", "2026-09-21").param("endDate", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-09-21"))
                .andExpect(jsonPath("$[0].state").value("TX"))
                .andExpect(jsonPath("$[0].orders").value(12));
    }

    @Test
    void badDatesAre400WithoutTouchingBigQuery() throws Exception {
        mvc.perform(get("/api/v1/orders/divisions").param("startDate", "09/21/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for 'startDate'. Dates must be YYYY-MM-DD."));
        mvc.perform(get("/api/v1/orders/divisions").param("startDate", "2026-09-21").param("endDate", "2026-09-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void bigQueryFailuresAre503WithoutLeakingDetails() throws Exception {
        when(repository.countPlacedOrdersByStateAndDay(any()))
                .thenThrow(new BigQueryException(403, "Access Denied: Table oms-prod:oms_events.op_order_events"));

        mvc.perform(get("/api/v1/orders/divisions"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Order data is temporarily unavailable. Try again shortly."));
    }
}
