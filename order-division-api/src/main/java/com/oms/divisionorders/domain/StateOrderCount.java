package com.oms.divisionorders.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Orders placed on one business date for one ship-to state.
 *
 * @param state upper-cased 2-letter code, or {@code "UNKNOWN"} when the event had none
 * @param demand null when no demand column is configured
 */
public record StateOrderCount(LocalDate date, String state, long orders, BigDecimal demand) {}
