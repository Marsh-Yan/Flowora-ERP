package com.flowora.erp.project;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimesheetEntityTest {
    @Test
    void calculatesCostAndBillableAmountFromHoursAndRates() {
        TimesheetEntity billable = new TimesheetEntity("org-a", "project-a", null, "user-a", LocalDate.now(), new BigDecimal("3.5"), new BigDecimal("40"), new BigDecimal("80"), true, "USD", "Design");
        TimesheetEntity internal = new TimesheetEntity("org-a", "project-a", null, "user-a", LocalDate.now(), new BigDecimal("2"), new BigDecimal("40"), new BigDecimal("80"), false, "USD", "Internal sync");

        assertEquals(new BigDecimal("140.0"), billable.costAmount());
        assertEquals(new BigDecimal("280.0"), billable.billableAmount());
        assertEquals(BigDecimal.ZERO, internal.billableAmount());
    }
}
