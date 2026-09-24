package com.oms.divisionorders.repository;

import com.oms.divisionorders.domain.DateRange;
import com.oms.divisionorders.domain.StateOrderCount;
import java.util.List;

public interface OrderEventsRepository {

    /** Orders placed in {@code range}, bucketed by business date and ship-to state. */
    List<StateOrderCount> countPlacedOrdersByStateAndDay(DateRange range);
}
