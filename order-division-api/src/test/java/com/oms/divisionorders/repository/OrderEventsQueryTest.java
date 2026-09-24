package com.oms.divisionorders.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oms.divisionorders.TestProps;
import com.oms.divisionorders.config.OrderEventsProperties.Columns;
import org.junit.jupiter.api.Test;

class OrderEventsQueryTest {

    @Test
    void buildsDedupedDailyStateCountsWithBoundParameters() {
        String sql = OrderEventsQuery.build(TestProps.props());

        assertThat(sql)
                .contains("FROM `oms-prod.oms_events.op_order_events` AS e")
                .contains("e.event_type IN UNNEST(@event_types)")
                .contains("e.event_ts >= TIMESTAMP(@start_date, @tz)")
                .contains("e.event_ts < TIMESTAMP(DATE_ADD(@end_date, INTERVAL 1 DAY), @tz)")
                .contains("QUALIFY ROW_NUMBER() OVER (PARTITION BY e.order_id ORDER BY e.event_ts) = 1")
                .contains("DATE(event_ts, @tz) AS order_date")
                .contains("CAST(NULL AS NUMERIC) AS demand")
                .doesNotContain("@country")
                .doesNotContain("INTERVAL 1 DAY) AND DATE_ADD");
    }

    @Test
    void addsOptionalColumnsWhenConfigured() {
        Columns cols = new Columns("header.order_no", "event_type", "event_ts", "payload.ship_to.state",
                "payload.ship_to.country", "order_total", "event_date");
        String sql = OrderEventsQuery.build(TestProps.props(cols));

        assertThat(sql)
                .contains("e.payload.ship_to.state AS ship_to_state")
                .contains("CAST(e.order_total AS NUMERIC) AS demand")
                .contains("UPPER(e.payload.ship_to.country) = @country")
                .contains("e.event_date BETWEEN DATE_SUB(@start_date, INTERVAL 1 DAY) AND DATE_ADD(@end_date, INTERVAL 1 DAY)")
                .contains("PARTITION BY e.header.order_no");
    }

    @Test
    void rejectsIdentifiersThatCouldInjectSql() {
        assertThatThrownBy(() -> new Columns("order_id; DROP TABLE x", "event_type", "event_ts", "s", "", "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns.order-id");
        assertThatThrownBy(() -> new Columns("order_id", "event_type", "event_ts", "s", "", "amt`", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
