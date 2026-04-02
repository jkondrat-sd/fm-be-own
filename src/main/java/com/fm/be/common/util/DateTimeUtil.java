package com.fm.be.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for IST (Indian Standard Time) operations.
 * All operations assume UTC storage and IST display.
 */
public final class DateTimeUtil {

    /** IST timezone (Asia/Kolkata, UTC+5:30) */
    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    /** UTC timezone */
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    /** ISO 8601 formatter with offset */
    public static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private DateTimeUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Converts a UTC Instant to IST ZonedDateTime.
     *
     * @param instant the UTC instant
     * @return ZonedDateTime in IST
     */
    public static ZonedDateTime toIst(Instant instant) {
        return instant.atZone(IST_ZONE);
    }

    /**
     * Returns the current time in IST.
     *
     * @return current ZonedDateTime in IST
     */
    public static ZonedDateTime nowIst() {
        return ZonedDateTime.now(IST_ZONE);
    }

    /**
     * Returns today's date in IST.
     *
     * @return today's LocalDate in IST
     */
    public static LocalDate todayIst() {
        return LocalDate.now(IST_ZONE);
    }

    /**
     * Returns the current time in IST.
     *
     * @return current LocalTime in IST
     */
    public static LocalTime currentTimeIst() {
        return LocalTime.now(IST_ZONE);
    }

    /**
     * Formats an Instant as an ISO 8601 string with IST offset.
     *
     * @param instant the UTC instant
     * @return formatted string like "2026-03-30T10:30:00+05:30"
     */
    public static String formatIst(Instant instant) {
        return toIst(instant).format(ISO_FORMATTER);
    }
}
