// State -> division mapping. Defaults to the 9 US Census Bureau divisions.
// If your org uses its own divisions/regions, edit DIVISIONS only — the map,
// ranking, trend and table all derive from this list.

export const DIVISIONS = [
  { id: "NE",  name: "New England",        states: ["CT", "ME", "MA", "NH", "RI", "VT"] },
  { id: "MA",  name: "Middle Atlantic",    states: ["NJ", "NY", "PA"] },
  { id: "ENC", name: "East North Central", states: ["IL", "IN", "MI", "OH", "WI"] },
  { id: "WNC", name: "West North Central", states: ["IA", "KS", "MN", "MO", "NE", "ND", "SD"] },
  { id: "SA",  name: "South Atlantic",     states: ["DE", "DC", "FL", "GA", "MD", "NC", "SC", "VA", "WV"] },
  { id: "ESC", name: "East South Central", states: ["AL", "KY", "MS", "TN"] },
  { id: "WSC", name: "West South Central", states: ["AR", "LA", "OK", "TX"] },
  { id: "MTN", name: "Mountain",           states: ["AZ", "CO", "ID", "MT", "NV", "NM", "UT", "WY"] },
  { id: "PAC", name: "Pacific",            states: ["AK", "CA", "HI", "OR", "WA"] },
];

// Postal code -> FIPS id used by the us-atlas topology.
export const STATE_FIPS = {
  AL: "01", AK: "02", AZ: "04", AR: "05", CA: "06", CO: "08", CT: "09", DE: "10",
  DC: "11", FL: "12", GA: "13", HI: "15", ID: "16", IL: "17", IN: "18", IA: "19",
  KS: "20", KY: "21", LA: "22", ME: "23", MD: "24", MA: "25", MI: "26", MN: "27",
  MS: "28", MO: "29", MT: "30", NE: "31", NV: "32", NH: "33", NJ: "34", NM: "35",
  NY: "36", NC: "37", ND: "38", OH: "39", OK: "40", OR: "41", PA: "42", RI: "44",
  SC: "45", SD: "46", TN: "47", TX: "48", UT: "49", VT: "50", VA: "51", WA: "53",
  WV: "54", WI: "55", WY: "56",
};

export const DIVISION_BY_STATE = Object.fromEntries(
  DIVISIONS.flatMap((d) => d.states.map((s) => [s, d.id]))
);

export const DIVISION_BY_FIPS = Object.fromEntries(
  Object.entries(STATE_FIPS).map(([state, fips]) => [fips, DIVISION_BY_STATE[state]])
);
