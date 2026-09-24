// Data loading for the division orders map.
//
// Data contract — the API (or sample generator) returns one row per
// (business date, ship-to state):
//
//   [{ "date": "2026-09-18", "state": "CA", "orders": 8123, "demand": 912345.67 }, ...]
//
//   date    order-placed business date (YYYY-MM-DD) in CONFIG.timeZone
//   state   2-letter postal code of the ship-to state
//   orders  count of distinct orders placed
//   demand  gross demand in USD (optional; the Demand metric is hidden if absent)
//
// Rows for states that aren't in DIVISIONS (PR, GU, APO/FPO, etc.) are counted
// as "Unmapped" and shown in the footnote rather than silently dropped.

export const CONFIG = {
  // Set via ?api=https://host/path or window.ORDER_MAP_CONFIG = { apiUrl }.
  // The page calls `${apiUrl}?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`.
  apiUrl:
    new URLSearchParams(location.search).get("api") ||
    window.ORDER_MAP_CONFIG?.apiUrl ||
    null,
  days: 7,
  // Business-day boundary for "placed in the last 7 days".
  timeZone: window.ORDER_MAP_CONFIG?.timeZone || "America/New_York",
  // true: window is today + previous 6 days (today is partial).
  // false: the 7 most recent complete days.
  includeToday: window.ORDER_MAP_CONFIG?.includeToday ?? true,
};

/** The CONFIG.days business dates in the window, oldest first, as YYYY-MM-DD. */
export function windowDates(now = new Date()) {
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: CONFIG.timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
  const end = new Date(`${today}T12:00:00Z`);
  if (!CONFIG.includeToday) end.setUTCDate(end.getUTCDate() - 1);
  return Array.from({ length: CONFIG.days }, (_, i) => {
    const d = new Date(end);
    d.setUTCDate(end.getUTCDate() - (CONFIG.days - 1 - i));
    return d.toISOString().slice(0, 10);
  });
}

export async function loadOrders(dates) {
  if (!CONFIG.apiUrl) return { rows: sampleRows(dates), sample: true };
  const url = new URL(CONFIG.apiUrl, location.href);
  url.searchParams.set("startDate", dates[0]);
  url.searchParams.set("endDate", dates[dates.length - 1]);
  const res = await fetch(url, { credentials: "include" });
  if (!res.ok) throw new Error(`Order API returned ${res.status}`);
  const body = await res.json();
  const rows = Array.isArray(body) ? body : body.rows;
  if (!Array.isArray(rows)) throw new Error("Order API response has no rows array");
  return { rows, sample: false };
}

// ---- sample data -----------------------------------------------------------

// Rough relative order volume per state (population-shaped), for the demo only.
const SAMPLE_WEIGHT = {
  CA: 39, TX: 30, FL: 22, NY: 20, PA: 13, IL: 12.5, OH: 11.8, GA: 11, NC: 10.7,
  MI: 10, NJ: 9.3, VA: 8.7, WA: 7.8, AZ: 7.4, TN: 7, MA: 7, IN: 6.8, MD: 6.2,
  MO: 6.2, WI: 5.9, CO: 5.8, MN: 5.7, SC: 5.3, AL: 5.1, LA: 4.6, KY: 4.5,
  OR: 4.2, OK: 4, CT: 3.6, UT: 3.4, IA: 3.2, NV: 3.2, AR: 3, MS: 2.9, KS: 2.9,
  NM: 2.1, NE: 2, ID: 1.9, WV: 1.8, HI: 1.4, NH: 1.4, ME: 1.4, MT: 1.1, RI: 1.1,
  DE: 1, SD: 0.9, ND: 0.8, AK: 0.7, DC: 0.7, VT: 0.6, WY: 0.6, PR: 0.9,
};

function sampleRows(dates) {
  // Deterministic PRNG so the demo is stable across reloads.
  let seed = 20260924;
  const rand = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 2 ** 32);
  const rows = [];
  for (const date of dates) {
    const dow = new Date(`${date}T12:00:00Z`).getUTCDay();
    const dayFactor = dow === 0 || dow === 6 ? 1.22 : dow === 1 ? 1.08 : 1;
    for (const [state, w] of Object.entries(SAMPLE_WEIGHT)) {
      const orders = Math.round(w * 190 * dayFactor * (0.85 + rand() * 0.3));
      const aov = 88 + rand() * 34;
      rows.push({ date, state, orders, demand: Math.round(orders * aov * 100) / 100 });
    }
  }
  return rows;
}
