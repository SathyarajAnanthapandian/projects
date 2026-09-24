# Division orders map

A static web page that maps orders placed in the **last 7 days** across US
divisions. By default these are the 9 US Census Bureau divisions, keyed by
ship-to state.

- **Choropleth map.** Each division is shaded by orders placed or by demand $,
  with its value labelled on the map. Hover or focus a division for orders,
  demand, AOV, share and the list of its states.
- **Ranked list.** Divisions sorted by the chosen metric, with their share of
  the total.
- **Daily trend.** Seven daily columns, for all divisions or for the one you
  select. Click a column to filter the whole page to that day. Today's column
  is drawn lighter because the day isn't over yet.
- **Filters.** The day and metric filters apply to the KPIs, map, ranking and
  table together. Click a division on the map or in the ranking to select it.
- **Table view.** Every number on the page is also in a table, broken out by
  day.
- **Light and dark themes**, keyboard navigation, and a layout that works on a
  phone.

## Run it

It needs no build step. Serve the folder from any static server:

```sh
cd division-orders-map
python3 -m http.server 8080
# open http://localhost:8080
```

With no API configured, the page shows **sample data** and a "Sample data"
badge.

## Connect real data

Point the page at an endpoint that returns one row per business date and
ship-to state:

```
GET <apiUrl>?startDate=2026-09-17&endDate=2026-09-23

[
  { "date": "2026-09-17", "state": "CA", "orders": 8123, "demand": 912345.67 },
  ...
]
```

The Java service in `../order-division-api` serves exactly this from BigQuery
at `/api/v1/orders/by-state`. It also has a server-side division rollup at
`/api/v1/orders/divisions`.

The endpoint can also return `{ "rows": [...] }`. `demand` is optional. If it's
missing, the Demand metric is hidden. `sql/orders_by_state_last_7_days.sql` is
a starting query for the endpoint.

You can set the API URL in either of two ways:

- a query string: `index.html?api=https://oms-reporting.internal/orders/by-state`
- or a config script placed before `app.js` in `index.html`:

  ```html
  <script>
    window.ORDER_MAP_CONFIG = {
      apiUrl: "/api/orders/by-state",
      timeZone: "America/New_York", // business-day boundary
      includeToday: true,           // false = the 7 most recent complete days
    };
  </script>
  ```

Rows for ship-to states outside the 50 states + DC (such as PR or APO/FPO) are
not dropped silently. The map leaves them out and the footnote gives their
count.

## Customising divisions

`divisions.js` holds the state → division mapping. To use your own
regions or divisions, edit the `DIVISIONS` list. The map merges state shapes
into the new regions automatically. A label that lands in an awkward spot can
be moved in `LABEL_OVERRIDES` in `app.js`.

## Files

| File | Purpose |
|---|---|
| `index.html`, `styles.css` | Page layout and theme tokens (light and dark) |
| `app.js` | Aggregation, map, ranking, trend, table, tooltips |
| `data.js` | Data contract, API fetch, 7-day window, sample data |
| `divisions.js` | State → division mapping |
| `data/states-albers-10m.json` | US state shapes ([us-atlas](https://github.com/topojson/us-atlas), ISC), stored in the repo so the page doesn't have to fetch map data from a CDN |
| `sql/orders_by_state_last_7_days.sql` | Example query for the endpoint |

d3 and topojson-client load from jsDelivr. If your network blocks public CDNs,
host both files yourself and change the two `<script>` tags.
