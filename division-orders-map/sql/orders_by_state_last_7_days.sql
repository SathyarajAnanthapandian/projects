-- Feeds the division orders map: one row per (business date, ship-to state)
-- for orders placed in the last 7 days. The page rolls states up to divisions.
--
-- Table and column names are placeholders. Map them to your order header /
-- order line tables. Written for BigQuery. Notes for other engines are at the
-- bottom.
--
-- Parameters: @start_date, @end_date (DATE, inclusive, business dates in ET).
-- The page sends them as ?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD.

SELECT
  DATE(o.order_placed_ts, 'America/New_York')      AS date,
  UPPER(o.ship_to_state_code)                      AS state,
  COUNT(DISTINCT o.order_id)                       AS orders,
  ROUND(SUM(o.gross_demand_amount), 2)             AS demand
FROM orders o
WHERE
  -- Filter on the raw timestamp so the partition/index is still used, then
  -- trim to exact ET business dates.
  o.order_placed_ts >= TIMESTAMP(DATE_SUB(@start_date, INTERVAL 1 DAY))
  AND o.order_placed_ts <  TIMESTAMP(DATE_ADD(@end_date, INTERVAL 2 DAY))
  AND DATE(o.order_placed_ts, 'America/New_York') BETWEEN @start_date AND @end_date
  AND o.ship_to_country_code = 'US'
  -- Decide what counts as "placed": typically exclude test/fraud-rejected orders
  -- but keep later cancellations, since they were still placed.
  AND COALESCE(o.is_test_order, FALSE) = FALSE
GROUP BY 1, 2
ORDER BY 1, 2;

-- Split shipments: if one order ships to several states, COUNT(DISTINCT order_id)
-- counts it once per state, so the division totals can add up to more than
-- the national total. To avoid that, use a single ship-to per order (for example
-- the first fulfillment group's address), or count shipments/fulfillment groups.
--
-- Other engines:
--   Postgres:   (o.order_placed_ts AT TIME ZONE 'America/New_York')::date
--   Snowflake:  CONVERT_TIMEZONE('America/New_York', o.order_placed_ts)::date
--   SQL Server: CAST(o.order_placed_ts AT TIME ZONE 'UTC' AT TIME ZONE 'Eastern Standard Time' AS date)
--   Oracle:     TRUNC(FROM_TZ(CAST(o.order_placed_ts AS TIMESTAMP), 'UTC') AT TIME ZONE 'America/New_York')
