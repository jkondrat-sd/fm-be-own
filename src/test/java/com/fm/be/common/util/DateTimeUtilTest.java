package com.fm.be.common.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DateTimeUtil}.
 */
class DateTimeUtilTest {

    @Test
    void toIst_convertsUtcInstantToIst() {
        // 2026-03-30T05:00:00Z should be 2026-03-30T10:30:00+05:30 in IST
        Instant utcInstant = Instant.parse("2026-03-30T05:00:00Z");
        ZonedDateTime ist = DateTimeUtil.toIst(utcInstant);

        assertEquals(ZoneId.of("Asia/Kolkata"), ist.getZone());
        assertEquals(10, ist.getHour());
        assertEquals(30, ist.getMinute());
    }

    @Test
    void nowIst_returnsCurrentTimeInIst() {
        ZonedDateTime now = DateTimeUtil.nowIst();

        assertEquals(ZoneId.of("Asia/Kolkata"), now.getZone());
    }

    @Test
    void todayIst_returnsCurrentDateInIst() {
        LocalDate today = DateTimeUtil.todayIst();

        assertNotNull(today);
        // Can't assert exact date since it depends on current time
    }

    @Test
    void formatIst_returnsIso8601WithOffset() {
        Instant utcInstant = Instant.parse("2026-03-30T05:00:00Z");
        String formatted = DateTimeUtil.formatIst(utcInstant);

        assertEquals("2026-03-30T10:30:00+05:30", formatted);
    }

    @Test
    void istZoneConstant_isCorrect() {
        assertEquals(ZoneId.of("Asia/Kolkata"), DateTimeUtil.IST_ZONE);
    }

    @Test
    void utcZoneConstant_isCorrect() {
        assertEquals(ZoneId.of("UTC"), DateTimeUtil.UTC_ZONE);
    }
}
