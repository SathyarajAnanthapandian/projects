/* global d3, topojson */
import { DIVISIONS, DIVISION_BY_FIPS, DIVISION_BY_STATE } from "./divisions.js";
import { CONFIG, loadOrders, windowDates } from "./data.js";

const METRICS = {
  orders: { label: "Orders placed", short: "Orders", fmt: (v) => fmtInt(v), compact: (v) => fmtCompact(v) },
  demand: { label: "Demand", short: "Demand", fmt: (v) => fmtUsd(v), compact: (v) => "$" + fmtCompact(v) },
};

// Hand-placed labels (coordinates in the 975x610 Albers frame). Divisions too
// small to hold a label get one off the coast with a leader line. East North
// Central's centroid falls on Lake Michigan and Pacific's on the CA/OR line.
const LABEL_OVERRIDES = {
  NE: { x: 958, y: 70, anchor: "end", leader: true },
  MA: { x: 958, y: 262, anchor: "end", leader: true },
  ENC: { x: 690, y: 250, anchor: "middle" },
  PAC: { x: 78, y: 300, anchor: "middle" },
};

const state = { metric: "orders", day: "all", selected: null, hovered: null };
let model; // { dates, divisions: Map<id, agg>, unmapped, sample, today }
let geo; // { features: Map<id, feature>, labelPos: Map<id, [x,y]>, stateMesh }

const $ = (id) => document.getElementById(id);

init().catch((err) => {
  console.error(err);
  const el = $("error");
  el.textContent = `Couldn't load order data: ${err.message}`;
  el.hidden = false;
});

async function init() {
  const dates = windowDates();
  const [topology, { rows, sample }] = await Promise.all([
    d3.json("data/states-albers-10m.json"),
    loadOrders(dates),
  ]);
  geo = buildGeometry(topology);
  model = aggregate(rows, dates, sample);

  buildControls();
  drawMapBase();
  render();

  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", render);
  new ResizeObserver(() => drawTrend()).observe($("trend"));
}

// ---- data ------------------------------------------------------------------

function aggregate(rows, dates, sample) {
  const inWindow = new Set(dates);
  const divisions = new Map(
    DIVISIONS.map((d) => [
      d.id,
      { ...d, byDay: new Map(dates.map((dt) => [dt, { orders: 0, demand: 0 }])) },
    ])
  );
  const unmapped = { orders: 0, states: new Set() };
  let hasDemand = false;

  for (const r of rows) {
    if (!inWindow.has(r.date)) continue;
    const stateCode = String(r.state || "").toUpperCase();
    const div = divisions.get(DIVISION_BY_STATE[stateCode]);
    const orders = Number(r.orders) || 0;
    if (!div) {
      unmapped.orders += orders;
      unmapped.states.add(stateCode || "unknown");
      continue;
    }
    const day = div.byDay.get(r.date);
    day.orders += orders;
    if (r.demand != null) {
      day.demand += Number(r.demand) || 0;
      hasDemand = true;
    }
  }
  return { dates, divisions, unmapped, sample, hasDemand, today: windowDates().at(-1) };
}

/** Totals for one division under the current day filter. */
function totals(div) {
  if (state.day !== "all") return div.byDay.get(state.day);
  let orders = 0, demand = 0;
  for (const d of div.byDay.values()) { orders += d.orders; demand += d.demand; }
  return { orders, demand };
}

function currentRows() {
  const list = [...model.divisions.values()].map((d) => ({ div: d, ...totals(d) }));
  const grand = list.reduce((a, r) => ({ orders: a.orders + r.orders, demand: a.demand + r.demand }), { orders: 0, demand: 0 });
  for (const r of list) r.share = grand[state.metric] ? r[state.metric] / grand[state.metric] : 0;
  list.sort((a, b) => b[state.metric] - a[state.metric]);
  return { list, grand };
}

// ---- geometry --------------------------------------------------------------

function buildGeometry(topology) {
  const obj = topology.objects.states;
  const path = d3.geoPath();
  const features = new Map();
  const labelPos = new Map();
  for (const div of DIVISIONS) {
    const geoms = obj.geometries.filter((g) => DIVISION_BY_FIPS[g.id] === div.id);
    const merged = topojson.merge(topology, geoms);
    features.set(div.id, { type: "Feature", id: div.id, geometry: merged });
    // Label at the centroid of the division's largest polygon (keeps Pacific's
    // label on the mainland rather than between Alaska and Hawaii).
    const polys = merged.type === "MultiPolygon" ? merged.coordinates : [merged.coordinates];
    const largest = d3.greatest(polys, (p) => path.area({ type: "Polygon", coordinates: p }));
    labelPos.set(div.id, path.centroid({ type: "Polygon", coordinates: largest }));
  }
  const stateMesh = topojson.mesh(
    topology, obj,
    (a, b) => a !== b && DIVISION_BY_FIPS[a.id] === DIVISION_BY_FIPS[b.id]
  );
  return { features, labelPos, stateMesh, path };
}

// ---- controls --------------------------------------------------------------

function buildControls() {
  const sel = $("day-filter");
  const fmtDay = (dt) =>
    new Date(`${dt}T12:00:00Z`).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric", timeZone: "UTC" });
  sel.append(new Option(`All ${model.dates.length} days`, "all"));
  for (const dt of [...model.dates].reverse()) {
    const partial = CONFIG.includeToday && dt === model.today ? " (today, partial)" : "";
    sel.append(new Option(fmtDay(dt) + partial, dt));
  }
  sel.addEventListener("change", () => { state.day = sel.value; render(); });

  const metricButtons = document.querySelectorAll(".segmented button");
  for (const btn of metricButtons) {
    if (btn.dataset.metric === "demand" && !model.hasDemand) btn.hidden = true;
    btn.addEventListener("click", () => {
      state.metric = btn.dataset.metric;
      metricButtons.forEach((b) => b.setAttribute("aria-checked", String(b === btn)));
      render();
    });
  }
  $("clear-selection").addEventListener("click", () => select(null));

  const first = fmtDay(model.dates[0]);
  const last = fmtDay(model.dates.at(-1));
  $("range-label").textContent = `Placed ${first} – ${last} · ${tzName()}`;
  $("sample-badge").hidden = !model.sample;
  $("updated").textContent = `Updated ${new Date().toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit", timeZone: CONFIG.timeZone })}`;
}

function select(id) {
  state.selected = state.selected === id ? null : id;
  render();
}

// ---- render ----------------------------------------------------------------

function render() {
  const { list, grand } = currentRows();
  const m = METRICS[state.metric];
  const css = getComputedStyle(document.documentElement);
  const ramp = [1, 2, 3, 4, 5].map((i) => css.getPropertyValue(`--seq-${i}`).trim());

  const values = list.map((r) => r[state.metric]);
  const color = d3.scaleQuantize().domain(d3.extent(values)).nice().range(ramp);
  if (d3.min(values) === d3.max(values)) color.domain([0, d3.max(values) || 1]);

  drawKpis(grand, list);
  updateMap(list, color, css);
  drawLegend(color, m);
  drawRank(list, m);
  drawTrend();
  drawTable(list, grand);

  $("map-title").textContent = `${m.label} by division`;
  $("rank-title").textContent = `Ranked by ${m.short.toLowerCase()}`;
  const selection = state.selected && model.divisions.get(state.selected);
  $("clear-selection").hidden = !selection;
  $("selection-name").textContent = selection ? selection.name : "";

  const um = model.unmapped;
  $("footnote").textContent =
    um.orders > 0
      ? `${fmtInt(um.orders)} orders shipping outside the 50 states + DC (${[...um.states].sort().join(", ")}) are excluded from the map.`
      : "Divisions are US Census Bureau divisions, by ship-to state.";
}

function drawKpis(grand, list) {
  $("kpi-orders").textContent = fmtInt(grand.orders);
  $("kpi-demand").textContent = model.hasDemand ? fmtUsd(grand.demand) : "–";
  $("kpi-aov").textContent = model.hasDemand && grand.orders ? fmtUsd(grand.demand / grand.orders, 2) : "–";
  const top = list[0];
  $("kpi-top").textContent = top ? `${top.div.name} · ${(top.share * 100).toFixed(1)}%` : "–";
}

function drawMapBase() {
  const svg = d3.select("#map");
  svg.append("g").attr("class", "divisions")
    .selectAll("path")
    .data(DIVISIONS, (d) => d.id)
    .join("path")
    .attr("class", "division")
    .attr("d", (d) => geo.path(geo.features.get(d.id)))
    .attr("tabindex", 0)
    .attr("role", "button")
    .on("pointermove", (ev, d) => { setHover(d.id); showDivisionTooltip(d.id, ev.clientX, ev.clientY); })
    .on("pointerleave", () => { setHover(null); hideTooltip(); })
    .on("focus", function (ev, d) {
      const r = this.getBoundingClientRect();
      setHover(d.id);
      showDivisionTooltip(d.id, r.x + r.width / 2, r.y + r.height / 2);
    })
    .on("blur", () => { setHover(null); hideTooltip(); })
    .on("click", (ev, d) => select(d.id))
    .on("keydown", (ev, d) => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); select(d.id); } });

  svg.append("path").attr("class", "state-mesh").attr("d", geo.path(geo.stateMesh));
  svg.append("path").attr("class", "selected-outline");
  svg.append("g").attr("class", "labels");
}

function updateMap(list, color, css) {
  const byId = new Map(list.map((r) => [r.div.id, r]));
  const svg = d3.select("#map");
  svg.selectAll(".division")
    .attr("fill", (d) => color(byId.get(d.id)[state.metric]))
    .classed("dim", (d) => state.selected && state.selected !== d.id)
    .attr("aria-label", (d) => {
      const r = byId.get(d.id);
      return `${d.name}: ${METRICS[state.metric].fmt(r[state.metric])}, ${(r.share * 100).toFixed(1)}% of total`;
    })
    .attr("aria-pressed", (d) => String(state.selected === d.id));

  svg.select(".selected-outline").attr("d", state.selected ? geo.path(geo.features.get(state.selected)) : null);

  const ink = { dark: css.getPropertyValue("--text-primary").trim(), light: css.getPropertyValue("--surface-1").trim() };
  const darkMode = luminance(ink.dark) > 0.5; // primary ink is white in dark mode
  const inkFor = (fill) => (luminance(fill) > 0.32 ? (darkMode ? ink.light : ink.dark) : (darkMode ? ink.dark : ink.light));

  const labels = svg.select(".labels").selectAll("g.map-label")
    .data(DIVISIONS, (d) => d.id)
    .join((enter) => {
      const g = enter.append("g").attr("class", "map-label");
      g.append("path").attr("class", "leader");
      const t = g.append("text");
      t.append("tspan").attr("class", "name");
      t.append("tspan").attr("class", "val");
      return g;
    });

  labels.each(function (d) {
    const g = d3.select(this);
    const r = byId.get(d.id);
    const [cx, cy] = geo.labelPos.get(d.id);
    const o = LABEL_OVERRIDES[d.id];
    const x = o ? o.x : cx;
    const y = o ? o.y : cy;
    const outside = Boolean(o?.leader);
    const fill = outside ? css.getPropertyValue("--text-primary").trim() : inkFor(color(r[state.metric]));
    g.classed("dim", state.selected && state.selected !== d.id)
      .style("opacity", state.selected && state.selected !== d.id ? 0.45 : 1);
    g.select(".leader").attr("d", outside ? `M${cx},${cy}L${x - 88},${y - 4}` : null);
    const t = g.select("text").attr("text-anchor", o ? o.anchor : "middle").attr("fill", fill);
    t.select(".name").attr("x", x).attr("y", y - 7).text(d.name);
    t.select(".val").attr("x", x).attr("y", y + 9).text(METRICS[state.metric].compact(r[state.metric]));
  });
}

function drawLegend(color, m) {
  const el = $("legend");
  el.replaceChildren();
  const [lo, hi] = color.domain();
  const bins = color.range().map((c) => color.invertExtent(c));
  bins.forEach(([a], i) => {
    const step = document.createElement("div");
    step.className = "legend-step";
    const sw = document.createElement("div");
    sw.className = "legend-swatch";
    sw.style.background = color.range()[i];
    const lab = document.createElement("span");
    lab.textContent = m.compact(i === 0 ? lo : a);
    step.append(sw, lab);
    el.append(step);
  });
  const end = document.createElement("span");
  end.className = "legend-step";
  end.style.flex = "0 0 auto";
  end.style.paddingTop = "14px";
  end.textContent = m.compact(hi);
  el.append(end);
}

function drawRank(list, m) {
  const ol = $("rank");
  const max = d3.max(list, (r) => r[state.metric]) || 1;
  ol.replaceChildren(
    ...list.map((r) => {
      const li = document.createElement("li");
      li.tabIndex = 0;
      li.dataset.id = r.div.id;
      li.classList.toggle("selected", state.selected === r.div.id);
      li.classList.toggle("dim", Boolean(state.selected && state.selected !== r.div.id));
      li.classList.toggle("hover", state.hovered === r.div.id);
      li.setAttribute("aria-pressed", String(state.selected === r.div.id));

      const name = document.createElement("span");
      name.className = "r-name";
      name.textContent = r.div.name;
      const value = document.createElement("span");
      value.className = "r-value";
      value.textContent = m.compact(r[state.metric]);
      const share = document.createElement("span");
      share.className = "r-share";
      share.textContent = `${(r.share * 100).toFixed(1)}%`;
      value.append(share);
      const track = document.createElement("div");
      track.className = "r-track";
      const bar = document.createElement("div");
      bar.className = "r-bar";
      bar.style.width = `${(r[state.metric] / max) * 100}%`;
      track.append(bar);
      li.append(name, value, track);

      li.addEventListener("click", () => select(r.div.id));
      li.addEventListener("keydown", (ev) => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); select(r.div.id); } });
      li.addEventListener("pointerenter", () => setHover(r.div.id));
      li.addEventListener("pointerleave", () => setHover(null));
      return li;
    })
  );
}

function setHover(id) {
  state.hovered = id;
  d3.selectAll("#map .division").classed("hovered", (d) => d.id === id);
  for (const li of $("rank").children) li.classList.toggle("hover", li.dataset.id === id);
}

function drawTrend() {
  if (!model) return;
  const svg = d3.select("#trend");
  const width = svg.node().clientWidth || 360;
  const height = 180;
  const margin = { top: 8, right: 4, bottom: 34, left: 44 };
  svg.attr("viewBox", `0 0 ${width} ${height}`);

  const m = METRICS[state.metric];
  const div = state.selected && model.divisions.get(state.selected);
  $("trend-title").textContent = `Daily ${m.short.toLowerCase()} · ${div ? div.name : "all divisions"}`;

  const series = model.dates.map((dt) => {
    let v = 0;
    for (const d of div ? [div] : model.divisions.values()) v += d.byDay.get(dt)[state.metric];
    return { date: dt, value: v };
  });

  const x = d3.scaleBand().domain(model.dates).range([margin.left, width - margin.right]).paddingInner(0.3).paddingOuter(0.1);
  const y = d3.scaleLinear().domain([0, d3.max(series, (s) => s.value) || 1]).nice(3).range([height - margin.bottom, margin.top]);

  svg.selectChildren().remove();
  const g = svg.append("g");
  g.selectAll(".grid-line").data(y.ticks(3)).join("line")
    .attr("class", (d) => (d === 0 ? "baseline" : "grid-line"))
    .attr("x1", margin.left).attr("x2", width - margin.right)
    .attr("y1", (d) => y(d)).attr("y2", (d) => y(d));
  g.selectAll(".y-tick").data(y.ticks(3)).join("text")
    .attr("class", "axis-text").attr("x", margin.left - 6).attr("y", (d) => y(d)).attr("dy", "0.32em")
    .attr("text-anchor", "end").text((d) => m.compact(d));

  const bars = g.selectAll(".bar").data(series).join("g").attr("class", "bar");
  // Hit target spans the full column height, larger than the painted bar.
  bars.append("rect").attr("class", "trend-hit")
    .attr("x", (d) => x(d.date) - (x.step() * x.paddingInner()) / 2).attr("width", x.step())
    .attr("y", margin.top).attr("height", height - margin.bottom - margin.top)
    .attr("tabindex", 0).attr("role", "button")
    .attr("aria-label", (d) => `${fmtDate(d.date)}: ${m.fmt(d.value)}`)
    .on("pointermove", (ev, d) => showTrendTooltip(d, ev.clientX, ev.clientY))
    .on("pointerleave", hideTooltip)
    .on("focus", function (ev, d) { const r = this.getBoundingClientRect(); showTrendTooltip(d, r.x + r.width / 2, r.y + 20); })
    .on("blur", hideTooltip)
    .on("click", (ev, d) => setDay(d.date))
    .on("keydown", (ev, d) => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); setDay(d.date); } });
  bars.append("path")
    .attr("class", (d) => {
      const c = ["trend-bar"];
      if (CONFIG.includeToday && d.date === model.today) c.push("partial");
      if (state.day !== "all" && state.day !== d.date) c.push("inactive");
      return c.join(" ");
    })
    .attr("d", (d) => topRoundedBar(x(d.date), y(d.value), x.bandwidth(), y(0) - y(d.value), 4));

  const tickFmt = (dt) => new Date(`${dt}T12:00:00Z`).toLocaleDateString("en-US", { weekday: "short", timeZone: "UTC" });
  const dayFmt = (dt) => new Date(`${dt}T12:00:00Z`).toLocaleDateString("en-US", { month: "numeric", day: "numeric", timeZone: "UTC" });
  bars.append("text").attr("class", "axis-text").attr("text-anchor", "middle")
    .attr("x", (d) => x(d.date) + x.bandwidth() / 2).attr("y", height - margin.bottom + 14)
    .text((d) => tickFmt(d.date));
  bars.append("text").attr("class", "axis-text").attr("text-anchor", "middle")
    .attr("x", (d) => x(d.date) + x.bandwidth() / 2).attr("y", height - margin.bottom + 27)
    .text((d) => (CONFIG.includeToday && d.date === model.today ? "Today" : dayFmt(d.date)));
}

function setDay(dt) {
  state.day = state.day === dt ? "all" : dt;
  $("day-filter").value = state.day;
  render();
}

function drawTable(list, grand) {
  const table = $("table");
  table.replaceChildren();
  const thead = table.createTHead().insertRow();
  const heads = ["Division", "Orders", "Demand", "AOV", "Share", ...model.dates.map((dt) => `${fmtDate(dt)} orders`)];
  for (const h of heads) {
    const th = document.createElement("th");
    th.textContent = h;
    thead.append(th);
  }
  const tbody = table.createTBody();
  const cells = (label, t, share, byDate) => [
    label,
    fmtInt(t.orders),
    model.hasDemand ? fmtUsd(t.demand) : "–",
    model.hasDemand && t.orders ? fmtUsd(t.demand / t.orders, 2) : "–",
    `${(share * 100).toFixed(1)}%`,
    ...model.dates.map((dt) => fmtInt(byDate(dt))),
  ];
  for (const r of list) {
    const tr = tbody.insertRow();
    for (const c of cells(r.div.name, r, r.share, (dt) => r.div.byDay.get(dt).orders)) tr.insertCell().textContent = c;
  }
  const tf = table.createTFoot().insertRow();
  const allByDate = (dt) => d3.sum(list, (r) => r.div.byDay.get(dt).orders);
  for (const c of cells("Total", grand, 1, allByDate)) tf.insertCell().textContent = c;
}

// ---- tooltip ---------------------------------------------------------------

function showDivisionTooltip(id, px, py) {
  const { list } = currentRows();
  const r = list.find((x) => x.div.id === id);
  const m = METRICS[state.metric];
  const tip = $("tooltip");
  tip.replaceChildren(
    el("div", "t-value", m.fmt(r[state.metric])),
    el("div", "t-title", `${r.div.name} · ${state.day === "all" ? `last ${model.dates.length} days` : fmtDate(state.day)}`),
    row("Orders", fmtInt(r.orders)),
    ...(model.hasDemand ? [row("Demand", fmtUsd(r.demand)), row("Avg order value", r.orders ? fmtUsd(r.demand / r.orders, 2) : "–")] : []),
    row(`Share of ${m.short.toLowerCase()}`, `${(r.share * 100).toFixed(1)}%`),
    el("div", "t-states", r.div.states.join(" · "))
  );
  placeTooltip(tip, px, py);
}

function showTrendTooltip(d, px, py) {
  const m = METRICS[state.metric];
  const partial = CONFIG.includeToday && d.date === model.today ? " (partial day)" : "";
  const div = state.selected && model.divisions.get(state.selected);
  const tip = $("tooltip");
  tip.replaceChildren(
    el("div", "t-value", m.fmt(d.value)),
    el("div", "t-title", `${fmtDate(d.date)}${partial} · ${div ? div.name : "All divisions"}`),
    el("div", "t-states", state.day === d.date ? "Click to show all days" : "Click to filter to this day")
  );
  placeTooltip(tip, px, py);
}

function placeTooltip(tip, px, py) {
  tip.hidden = false;
  const { width, height } = tip.getBoundingClientRect();
  let left = px + 14;
  let top = py + 14;
  if (left + width > window.innerWidth - 8) left = px - width - 14;
  if (top + height > window.innerHeight - 8) top = py - height - 14;
  tip.style.left = `${Math.max(8, left)}px`;
  tip.style.top = `${Math.max(8, top)}px`;
}

function hideTooltip() {
  $("tooltip").hidden = true;
}

function el(tag, cls, text) {
  const n = document.createElement(tag);
  n.className = cls;
  n.textContent = text;
  return n;
}

function row(label, value) {
  const n = el("div", "t-row", "");
  const l = document.createElement("span");
  l.textContent = label;
  const b = document.createElement("b");
  b.textContent = value;
  n.append(l, b);
  return n;
}

// ---- helpers ---------------------------------------------------------------

function topRoundedBar(x, y, w, h, r) {
  if (h <= 0) return "";
  r = Math.min(r, w / 2, h);
  return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
}

function luminance(color) {
  const c = d3.rgb(color);
  const lin = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; };
  return 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b);
}

function tzName() {
  return new Intl.DateTimeFormat("en-US", { timeZone: CONFIG.timeZone, timeZoneName: "long" })
    .formatToParts(new Date()).find((p) => p.type === "timeZoneName").value;
}

function fmtDate(dt) {
  return new Date(`${dt}T12:00:00Z`).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric", timeZone: "UTC" });
}
function fmtInt(v) { return Math.round(v).toLocaleString("en-US"); }
function fmtUsd(v, digits = 0) {
  return v.toLocaleString("en-US", { style: "currency", currency: "USD", minimumFractionDigits: digits, maximumFractionDigits: digits });
}
function fmtCompact(v) {
  return v.toLocaleString("en-US", { notation: "compact", maximumFractionDigits: v >= 1e6 ? 2 : 1 });
}
