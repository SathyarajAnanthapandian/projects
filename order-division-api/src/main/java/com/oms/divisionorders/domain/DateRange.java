package com.oms.divisionorders.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Inclusive range of business dates. */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start.isAfter(end)) {
            throw new InvalidDateRangeException("startDate " + start + " is after endDate " + end);
        }
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    public List<LocalDate> dates() {
        return start.datesUntil(end.plusDays(1)).toList();
    }
}
