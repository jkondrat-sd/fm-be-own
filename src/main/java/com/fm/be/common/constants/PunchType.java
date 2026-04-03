package com.fm.be.common.constants;

/**
 * Enum representing the type of attendance punch.
 * Used by {@link com.fm.be.modules.attendance.entity.AttendanceLog}.
 */
public enum PunchType {
    /** Employee clocking in at site */
    IN,
    /** Employee clocking out from site */
    OUT
}
