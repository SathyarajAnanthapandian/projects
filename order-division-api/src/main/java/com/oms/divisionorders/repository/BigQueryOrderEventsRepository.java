package com.oms.divisionorders.repository;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import com.oms.divisionorders.config.OrderEventsProperties;
import com.oms.divisionorders.domain.DateRange;
import com.oms.divisionorders.domain.StateOrderCount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

@Repository
public class BigQueryOrderEventsRepository implements OrderEventsRepository {

    private static final Logger log = LoggerFactory.getLogger(BigQueryOrderEventsRepository.class);

    private final BigQuery bigQuery;
    private final OrderEventsProperties props;
    private final String sql;

    public BigQueryOrderEventsRepository(BigQuery bigQuery, OrderEventsProperties props) {
        this.bigQuery = bigQuery;
        this.props = props;
        this.sql = OrderEventsQuery.build(props);
        log.info("Order events source: {}", props.qualifiedTable());
    }

    /**
     * Results are cached briefly (see {@code spring.cache.caffeine.spec}). Every map
     * load would otherwise start a new BigQuery job, and each job scans and bills
     * the whole window.
     */
    @Override
    @Cacheable(cacheNames = "stateOrderCounts", key = "#range")
    public List<StateOrderCount> countPlacedOrdersByStateAndDay(DateRange range) {
        QueryJobConfiguration.Builder job = QueryJobConfiguration.newBuilder(sql)
                .setUseLegacySql(false)
                .setUseQueryCache(true)
                .setMaximumBytesBilled(props.maximumBytesBilled())
                .setLabels(Map.of("app", "order-division-api"))
                .addNamedParameter("start_date", QueryParameterValue.date(range.start().toString()))
                .addNamedParameter("end_date", QueryParameterValue.date(range.end().toString()))
                .addNamedParameter("tz", QueryParameterValue.string(props.timeZone().getId()))
                .addNamedParameter("event_types",
                        QueryParameterValue.array(props.placedEventTypes().toArray(String[]::new), String.class));
        if (!props.columns().shipToCountry().isEmpty()) {
            job.addNamedParameter("country", QueryParameterValue.string(props.country().toUpperCase()));
        }

        long started = System.nanoTime();
        TableResult result;
        try {
            result = bigQuery.query(job.build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for BigQuery", e);
        }

        List<StateOrderCount> rows = new ArrayList<>();
        for (FieldValueList row : result.iterateAll()) {
            FieldValue demand = row.get("demand");
            rows.add(new StateOrderCount(
                    LocalDate.parse(row.get("order_date").getStringValue()),
                    row.get("state").getStringValue(),
                    row.get("orders").getLongValue(),
                    demand.isNull() ? null : demand.getNumericValue()));
        }
        log.info("Loaded {} state/day rows for {}..{} in {} ms",
                rows.size(), range.start(), range.end(), (System.nanoTime() - started) / 1_000_000);
        return rows;
    }
}
