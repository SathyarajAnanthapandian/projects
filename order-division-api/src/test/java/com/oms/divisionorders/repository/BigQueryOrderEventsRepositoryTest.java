package com.oms.divisionorders.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import com.oms.divisionorders.TestProps;
import com.oms.divisionorders.config.OrderEventsProperties.Columns;
import com.oms.divisionorders.domain.DateRange;
import com.oms.divisionorders.domain.StateOrderCount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BigQueryOrderEventsRepositoryTest {

    private static final FieldList SCHEMA = FieldList.of(
            Field.of("order_date", StandardSQLTypeName.DATE),
            Field.of("state", StandardSQLTypeName.STRING),
            Field.of("orders", StandardSQLTypeName.INT64),
            Field.of("demand", StandardSQLTypeName.NUMERIC));

    @Test
    void bindsParametersAndMapsRows() throws Exception {
        Columns cols = new Columns("order_id", "event_type", "event_ts", "ship_to_state",
                "ship_to_country", "order_total", "");
        BigQuery bigQuery = mock(BigQuery.class);
        TableResult result = mock(TableResult.class);
        when(result.iterateAll()).thenReturn(List.of(
                row("2026-09-21", "CA", "42", "4200.50"),
                row("2026-09-21", "UNKNOWN", "1", null)));
        AtomicReference<QueryJobConfiguration> sent = new AtomicReference<>();
        when(bigQuery.query(any(QueryJobConfiguration.class))).thenAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return result;
        });

        var repo = new BigQueryOrderEventsRepository(bigQuery, TestProps.props(cols));
        List<StateOrderCount> rows = repo.countPlacedOrdersByStateAndDay(
                new DateRange(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 23)));

        assertThat(rows).containsExactly(
                new StateOrderCount(LocalDate.of(2026, 9, 21), "CA", 42, new BigDecimal("4200.50")),
                new StateOrderCount(LocalDate.of(2026, 9, 21), "UNKNOWN", 1, null));

        QueryJobConfiguration job = sent.get();
        assertThat(job.useLegacySql()).isFalse();
        assertThat(job.getMaximumBytesBilled()).isEqualTo(50_000_000_000L);
        var params = job.getNamedParameters();
        assertThat(params.get("start_date")).isEqualTo(QueryParameterValue.date("2026-09-17"));
        assertThat(params.get("end_date")).isEqualTo(QueryParameterValue.date("2026-09-23"));
        assertThat(params.get("tz")).isEqualTo(QueryParameterValue.string("America/New_York"));
        assertThat(params.get("country")).isEqualTo(QueryParameterValue.string("US"));
        assertThat(params.get("event_types").getArrayValues())
                .extracting(QueryParameterValue::getValue).containsExactly("ORDER_CREATED");
    }

    private static FieldValueList row(String date, String state, String orders, String demand) {
        return FieldValueList.of(List.of(
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, date),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, state),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, orders),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, demand)), SCHEMA);
    }
}
