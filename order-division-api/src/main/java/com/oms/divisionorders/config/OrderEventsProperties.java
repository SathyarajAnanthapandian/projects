package com.oms.divisionorders.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the order events live in BigQuery and how to read them.
 *
 * <p>Every table and column name here is interpolated into SQL (BigQuery cannot
 * parameterize identifiers), so each one is checked against a strict pattern at
 * startup. Values such as dates and event types are always bound as query parameters.
 */
@Validated
@ConfigurationProperties(prefix = "oms.order-events")
public record OrderEventsProperties(
        @NotBlank String projectId,
        @NotBlank String dataset,
        @DefaultValue("op_order_events") String table,
        @Valid @NotNull @DefaultValue Columns columns,
        /** event_type values that mean "order placed". The first one per order is counted. */
        @NotEmpty @DefaultValue("ORDER_CREATED") List<String> placedEventTypes,
        /** Business-day boundary used to bucket orders by date. */
        @NotNull @DefaultValue("America/New_York") ZoneId timeZone,
        /** Window used when the caller doesn't pass dates: today plus (defaultDays - 1) previous days. */
        @Min(1) @DefaultValue("7") int defaultDays,
        @Min(1) @DefaultValue("92") int maxRangeDays,
        /** Only count orders shipping to this country. Needs {@code columns.shipToCountry}. */
        @DefaultValue("US") String country,
        /** Safety cap per query. A query that would scan more fails instead of billing. */
        @Min(1) @DefaultValue("50000000000") long maximumBytesBilled) {

    private static final Pattern PROJECT = Pattern.compile("[a-z][a-z0-9.:-]{2,62}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,1023}");
    private static final Pattern COLUMN_PATH =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    public OrderEventsProperties {
        require(PROJECT, projectId, "project-id");
        require(NAME, dataset, "dataset");
        require(NAME, table, "table");
    }

    /**
     * Column names in the events table. Dotted paths reach into STRUCTs,
     * for example {@code payload.ship_to.state_code}.
     */
    public record Columns(
            @DefaultValue("order_id") String orderId,
            @DefaultValue("event_type") String eventType,
            /** TIMESTAMP column holding when the event happened. */
            @DefaultValue("event_ts") String eventTs,
            @DefaultValue("ship_to_state") String shipToState,
            /** Optional. When blank, the country filter is skipped. */
            @DefaultValue("") String shipToCountry,
            /** Optional numeric order total. When blank, demand is returned as null. */
            @DefaultValue("") String demand,
            /**
             * Optional DATE partition column. Set this when the table is partitioned on
             * something other than {@code eventTs} so BigQuery can prune partitions.
             */
            @DefaultValue("") String partitionDate) {

        public Columns {
            require(COLUMN_PATH, orderId, "columns.order-id");
            require(COLUMN_PATH, eventType, "columns.event-type");
            require(COLUMN_PATH, eventTs, "columns.event-ts");
            require(COLUMN_PATH, shipToState, "columns.ship-to-state");
            optional(COLUMN_PATH, shipToCountry, "columns.ship-to-country");
            optional(COLUMN_PATH, demand, "columns.demand");
            optional(COLUMN_PATH, partitionDate, "columns.partition-date");
        }
    }

    public String qualifiedTable() {
        return "`" + projectId + "." + dataset + "." + table + "`";
    }

    private static void optional(Pattern p, String value, String name) {
        if (value != null && !value.isEmpty()) {
            require(p, value, name);
        }
    }

    private static void require(Pattern p, String value, String name) {
        if (value == null || !p.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "oms.order-events." + name + " is not a valid BigQuery identifier: " + value);
        }
    }
}
