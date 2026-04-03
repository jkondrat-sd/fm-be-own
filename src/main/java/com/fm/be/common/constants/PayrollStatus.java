package com.fm.be.common.constants;

/**
 * Lifecycle states for a payroll run.
 *
 * <p>Flow: DRAFT → PROCESSING → PENDING_APPROVAL → APPROVED/REJECTED → DISBURSED</p>
 */
public enum PayrollStatus {
    /** Initial state when payroll run is created */
    DRAFT,
    /** Async background job is calculating payslips */
    PROCESSING,
    /** All payslips computed, awaiting admin approval */
    PENDING_APPROVAL,
    /** Admin has approved the payroll run */
    APPROVED,
    /** Admin has rejected the payroll run */
    REJECTED,
    /** Funds have been disbursed to employees */
    DISBURSED
}
