package com.oms.divisionorders.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US Census Bureau divisions, keyed by ship-to state. The ids match
 * {@code division-orders-map/divisions.js} so the map and the API agree.
 *
 * <p>{@code lat}/{@code lng} give an approximate center of each division's
 * contiguous-US area. Use it to place a cluster marker. It is not a boundary.
 */
public enum Division {
    NE("New England", 43.9, -71.6, "CT", "ME", "MA", "NH", "RI", "VT"),
    MA("Middle Atlantic", 41.4, -76.3, "NJ", "NY", "PA"),
    ENC("East North Central", 42.1, -86.4, "IL", "IN", "MI", "OH", "WI"),
    WNC("West North Central", 43.3, -97.4, "IA", "KS", "MN", "MO", "NE", "ND", "SD"),
    SA("South Atlantic", 33.6, -80.9, "DE", "DC", "FL", "GA", "MD", "NC", "SC", "VA", "WV"),
    ESC("East South Central", 34.8, -87.4, "AL", "KY", "MS", "TN"),
    WSC("West South Central", 31.9, -95.6, "AR", "LA", "OK", "TX"),
    MTN("Mountain", 40.4, -111.2, "AZ", "CO", "ID", "MT", "NV", "NM", "UT", "WY"),
    PAC("Pacific", 41.2, -121.3, "AK", "CA", "HI", "OR", "WA");

    private static final Map<String, Division> BY_STATE = Arrays.stream(values())
            .flatMap(d -> d.states.stream().map(s -> Map.entry(s, d)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, Division> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Division::name, Function.identity()));

    private final String displayName;
    private final double lat;
    private final double lng;
    private final List<String> states;

    Division(String displayName, double lat, double lng, String... states) {
        this.displayName = displayName;
        this.lat = lat;
        this.lng = lng;
        this.states = List.of(states);
    }

    public String displayName() {
        return displayName;
    }

    public double lat() {
        return lat;
    }

    public double lng() {
        return lng;
    }

    public List<String> states() {
        return states;
    }

    /** Division for a 2-letter state code. Empty for PR, GU, APO/FPO, bad data, etc. */
    public static Optional<Division> forState(String stateCode) {
        return stateCode == null ? Optional.empty() : Optional.ofNullable(BY_STATE.get(stateCode));
    }

    public static Optional<Division> forId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id.toUpperCase()));
    }
}
