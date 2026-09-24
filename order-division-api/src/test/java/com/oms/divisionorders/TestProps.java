package com.oms.divisionorders;

import com.oms.divisionorders.config.OrderEventsProperties;
import com.oms.divisionorders.config.OrderEventsProperties.Columns;
import java.time.ZoneId;
import java.util.List;

public final class TestProps {

    private TestProps() {}

    public static Columns columns() {
        return new Columns("order_id", "event_type", "event_ts", "ship_to_state", "", "", "");
    }

    public static OrderEventsProperties props() {
        return props(columns());
    }

    public static OrderEventsProperties props(Columns columns) {
        return new OrderEventsProperties("oms-prod", "oms_events", "op_order_events", columns,
                List.of("ORDER_CREATED"), ZoneId.of("America/New_York"), 7, 92, "US", 50_000_000_000L);
    }
}
