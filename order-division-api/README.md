# order-division-api

A Spring Boot 3 / Java 21 service that counts **orders placed per US division**
from the BigQuery table `op_order_events`. It is built to feed a map, with one
cluster marker per division sized by order count.

## Endpoints

With no dates, each endpoint covers the **last 7 days including today**, where
"today" is the current date in `America/New_York`. You can pass
`startDate` / `endDate` (`YYYY-MM-DD`, inclusive) to choose another range of up
to 92 days.

| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/orders/divisions` | All 9 divisions sorted by order count, with marker coordinates, per-state and per-day breakdowns, and orders that map to no division |
| GET | `/api/v1/orders/divisions/{id}` | One division (`NE`, `MA`, `ENC`, `WNC`, `SA`, `ESC`, `WSC`, `MTN`, `PAC`) |
| GET | `/api/v1/orders/by-state` | Raw `{date, state, orders, demand}` rows. The `division-orders-map` page reads this format directly |
| GET | `/actuator/health` | Health check |

```jsonc
// GET /api/v1/orders/divisions
{
  "startDate": "2026-09-17",
  "endDate": "2026-09-23",
  "timeZone": "America/New_York",
  "totalOrders": 473432,
  "totalDemand": null,                 // set when columns.demand is configured
  "divisions": [
    {
      "id": "SA",
      "name": "South Atlantic",
      "lat": 33.6, "lng": -80.9,         // cluster marker position
      "orderCount": 96929,
      "demand": null,
      "share": 0.2047,                   // of totalOrders
      "states": [{ "state": "FL", "orderCount": 30112, "demand": null }, ...],
      "daily":  [{ "date": "2026-09-17", "orderCount": 12888, "demand": null }, ...]
    },
    ...
  ],
  "unmapped": { "orderCount": 1305, "states": ["PR", "UNKNOWN"] },
  "generatedAt": "2026-09-24T01:22:05Z"
}
```

Every division is always present. Every state and day inside a division is
always present too, with zeros where nothing was ordered. Errors come back as
RFC 7807 problem JSON: 400 for bad dates, 404 for an unknown division, and 503
when BigQuery fails. BigQuery's error details are logged, not returned to the
caller.

## How orders are counted

`op_order_events` holds many events per order. The query:

1. keeps events whose `event_type` is one of `placed-event-types` (default
   `ORDER_CREATED`) and whose `event_ts` falls within the requested days in
   Eastern time;
2. keeps only the **first** of those events per `order_id`, so a replayed or
   duplicated create event doesn't count the order twice;
3. buckets the orders by Eastern-time date and upper-cased ship-to state.

The service then rolls the states up into the 9 US Census divisions
(`domain/Division.java`, the same mapping the map page uses). Ship-to states
outside the 50 states + DC, and orders with no ship-to state, are reported
under `unmapped` rather than dropped.

The SQL is in `repository/OrderEventsQuery.java`. Dates, the time zone, event
types and the country are passed as bound query parameters. Table and column
names come from config and are checked against a strict identifier pattern at
startup.

## Configure

Everything is in `src/main/resources/application.yml`. **Check the column names
against your table.** The defaults are guesses:

```yaml
oms:
  order-events:
    project-id: ${BQ_PROJECT_ID}       # project that owns the table
    dataset: ${BQ_DATASET}
    table: op_order_events
    columns:
      order-id: order_id
      event-type: event_type
      event-ts: event_ts               # must be a TIMESTAMP
      ship-to-state: ship_to_state     # dotted paths work for STRUCTs: payload.ship_to.state
      ship-to-country: ""              # set to keep only orders shipping to the US
      demand: ""                       # set to return demand $ (e.g. order_total)
      partition-date: ""               # set if the table is partitioned on a DATE column
    placed-event-types: [ORDER_CREATED]
```

- **Cost.** Each query is capped by `maximum-bytes-billed` (50 GB by default).
  Results are cached for 5 minutes (`spring.cache.caffeine.spec`), so reloading
  the map doesn't start a new BigQuery job each time. If the table is partitioned
  on a column other than `event_ts`, set `partition-date` so BigQuery only scans
  the needed partitions.
- **Credentials.** The service uses Application Default Credentials. In GKE or
  Cloud Run, attach a service account with `roles/bigquery.jobUser` on the job
  project and `roles/bigquery.dataViewer` on the dataset. Set
  `BQ_JOB_PROJECT_ID` if query jobs should run in, and be billed to, a
  different project from the one that owns the data.
- **CORS.** `CORS_ALLOWED_ORIGINS=https://your-map-host` lets the map page call
  the API from another origin.
- **Auth.** The endpoints have no authentication of their own. Put the service
  behind your gateway or IAP, or add Spring Security, before exposing it.

## Run

```sh
gcloud auth application-default login     # local only
export BQ_PROJECT_ID=my-data-project BQ_DATASET=oms_events
mvn spring-boot:run
curl localhost:8080/api/v1/orders/divisions
```

Point the map page at this service:
`division-orders-map/index.html?api=http://localhost:8080/api/v1/orders/by-state`

## Test

```sh
mvn test
```

The tests cover the SQL builder (including rejecting unsafe identifiers),
query-parameter binding and row mapping against a mocked BigQuery client, the
division rollup, date-range rules, and the HTTP layer (JSON shape, 400/404/503).
Nothing in the tests runs against real BigQuery. Before relying on the numbers,
run the SQL from `OrderEventsQuery` once as a dry run in the BigQuery console
against your table.

## Known limits

- The first-event check only looks inside the requested window. If an order was
  created just before the window and a duplicate create event lands inside it,
  the order is counted in the window. Widen the lookback in the `placed` CTE if
  your event stream replays old events.
- An order is attributed to the ship-to state on its first placed event. Orders
  split across several ship-to addresses count once, under that first state.
