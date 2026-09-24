package com.oms.divisionorders.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Row shape the division map page expects: see division-orders-map/data.js. */
public record StateDayRow(LocalDate date, String state, long orders, BigDecimal demand) {}
