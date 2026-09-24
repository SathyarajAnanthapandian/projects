package com.oms.divisionorders.repository;

import com.oms.divisionorders.config.OrderEventsProperties;
import com.oms.divisionorders.config.OrderEventsProperties.Columns;

/**
 * Builds the BigQuery Standard SQL for placed-order counts.
 *
 * <p>{@code op_order_events} holds many events per order, and a "placed" event can
 * arrive more than once (retries, replays). So the query keeps only the first
 * placed event per order, then counts orders per business date and state.
 *
 * <p>Named parameters: {@code @start_date}, {@code @end_date} (DATE),
 * {@code @tz} (STRING), {@code @event_types} (ARRAY&lt;STRING&gt;) and
 * {@code @country} (STRING, only when a country column is configured).
 */
final class OrderEventsQuery {

    private OrderEventsQuery() {}

    static String build(OrderEventsProperties props) {
        Columns c = props.columns();
        String demand = c.demand().isEmpty() ? "CAST(NULL AS NUMERIC)" : "CAST(e." + c.demand() + " AS NUMERIC)";

        StringBuilder where = new StringBuilder()
                .append("    e.").append(c.eventType()).append(" IN UNNEST(@event_types)\n")
                .append("    AND e.").append(c.eventTs()).append(" >= TIMESTAMP(@start_date, @tz)\n")
                .append("    AND e.").append(c.eventTs())
                .append(" < TIMESTAMP(DATE_ADD(@end_date, INTERVAL 1 DAY), @tz)\n");
        if (!c.partitionDate().isEmpty()) {
            // Partition dates are usually UTC, so pad by a day on each side.
            where.append("    AND e.").append(c.partitionDate())
                    .append(" BETWEEN DATE_SUB(@start_date, INTERVAL 1 DAY) AND DATE_ADD(@end_date, INTERVAL 1 DAY)\n");
        }
        if (!c.shipToCountry().isEmpty()) {
            where.append("    AND UPPER(e.").append(c.shipToCountry()).append(") = @country\n");
        }

        return """
                WITH placed AS (
                  SELECT
                    e.%1$s AS event_ts,
                    e.%2$s AS ship_to_state,
                    %3$s AS demand
                  FROM %4$s AS e
                  WHERE
                %5$s  QUALIFY ROW_NUMBER() OVER (PARTITION BY e.%6$s ORDER BY e.%1$s) = 1
                )
                SELECT
                  DATE(event_ts, @tz) AS order_date,
                  COALESCE(NULLIF(UPPER(TRIM(ship_to_state)), ''), 'UNKNOWN') AS state,
                  COUNT(*) AS orders,
                  SUM(demand) AS demand
                FROM placed
                GROUP BY order_date, state
                ORDER BY order_date, state
                """.formatted(c.eventTs(), c.shipToState(), demand, props.qualifiedTable(), where, c.orderId());
    }
}
