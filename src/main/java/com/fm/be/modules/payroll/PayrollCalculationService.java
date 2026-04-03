package com.fm.be.modules.payroll;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for Indian statutory payroll calculations.
 *
 * <p>Computes PF, ESIC, TDS, and Professional Tax per Indian labor law.
 * All rates and thresholds should be stored as configurable data
 * (for now using constants; TASK-3.1 notes that DB config tables
 * will replace these in production).</p>
 *
 * <p>Key rules:</p>
 * <ul>
 *   <li>PF: 12% employee + employer, capped at ₹15,000 base</li>
 *   <li>ESIC: 0.75% employee + 3.25% employer if gross ≤ ₹21,000</li>
 *   <li>TDS: Old regime slab-based, monthly = annual / 12</li>
 *   <li>PT: Maharashtra brackets with February exception</li>
 * </ul>
 */
@Slf4j
@Service
public class PayrollCalculationService {

    // --- PF Configuration ---
    /** PF contribution rate (same for employee and employer) */
    private static final BigDecimal PF_RATE = new BigDecimal("0.12");
    /** Maximum basic salary for PF calculation */
    private static final BigDecimal PF_CAP = new BigDecimal("15000");

    // --- ESIC Configuration ---
    /** ESIC applicability threshold (gross monthly salary) */
    private static final BigDecimal ESIC_THRESHOLD = new BigDecimal("21000");
    /** ESIC employee contribution rate */
    private static final BigDecimal ESIC_EMPLOYEE_RATE = new BigDecimal("0.0075");
    /** ESIC employer contribution rate */
    private static final BigDecimal ESIC_EMPLOYER_RATE = new BigDecimal("0.0325");

    // --- TDS Slabs (Old Regime) ---
    private static final BigDecimal TDS_SLAB_1_LIMIT = new BigDecimal("250000");
    private static final BigDecimal TDS_SLAB_2_LIMIT = new BigDecimal("500000");
    private static final BigDecimal TDS_SLAB_3_LIMIT = new BigDecimal("1000000");
    private static final BigDecimal TDS_RATE_SLAB_2 = new BigDecimal("0.05");
    private static final BigDecimal TDS_RATE_SLAB_3 = new BigDecimal("0.20");
    private static final BigDecimal TDS_RATE_SLAB_4 = new BigDecimal("0.30");
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");

    // --- PT (Maharashtra) Configuration ---
    private static final BigDecimal PT_BRACKET_1_LIMIT = new BigDecimal("7500");
    private static final BigDecimal PT_BRACKET_2_LIMIT = new BigDecimal("10000");
    private static final BigDecimal PT_BRACKET_2_AMOUNT = new BigDecimal("175");
    private static final BigDecimal PT_BRACKET_3_AMOUNT = new BigDecimal("200");
    private static final BigDecimal PT_FEBRUARY_AMOUNT = new BigDecimal("300");

    /**
     * Calculates the employee's PF contribution.
     * Rate: 12% of Basic (capped at ₹15,000 base).
     *
     * @param basic the employee's monthly basic salary
     * @return the employee PF deduction amount
     */
    public BigDecimal calculatePfEmployee(BigDecimal basic) {
        BigDecimal pfBase = basic.min(PF_CAP);
        return pfBase.multiply(PF_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the employer's PF contribution.
     * Rate: 12% of Basic (capped at ₹15,000 base).
     * Split: 3.67% to EPF + 8.33% to EPS.
     *
     * @param basic the employee's monthly basic salary
     * @return the employer PF contribution amount
     */
    public BigDecimal calculatePfEmployer(BigDecimal basic) {
        BigDecimal pfBase = basic.min(PF_CAP);
        return pfBase.multiply(PF_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the employee's ESIC contribution.
     * Applicable only if gross salary ≤ ₹21,000/month.
     * Rate: 0.75% of gross.
     *
     * @param grossSalary the employee's monthly gross salary
     * @return the employee ESIC deduction, or zero if not applicable
     */
    public BigDecimal calculateEsicEmployee(BigDecimal grossSalary) {
        if (grossSalary.compareTo(ESIC_THRESHOLD) > 0) {
            return BigDecimal.ZERO;
        }
        return grossSalary.multiply(ESIC_EMPLOYEE_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the employer's ESIC contribution.
     * Applicable only if gross salary ≤ ₹21,000/month.
     * Rate: 3.25% of gross.
     *
     * @param grossSalary the employee's monthly gross salary
     * @return the employer ESIC contribution, or zero if not applicable
     */
    public BigDecimal calculateEsicEmployer(BigDecimal grossSalary) {
        if (grossSalary.compareTo(ESIC_THRESHOLD) > 0) {
            return BigDecimal.ZERO;
        }
        return grossSalary.multiply(ESIC_EMPLOYER_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates monthly TDS based on annual income (old regime slabs).
     *
     * <p>Slabs:</p>
     * <ul>
     *   <li>₹0 – ₹2,50,000: Nil</li>
     *   <li>₹2,50,001 – ₹5,00,000: 5%</li>
     *   <li>₹5,00,001 – ₹10,00,000: 20%</li>
     *   <li>Above ₹10,00,000: 30%</li>
     * </ul>
     *
     * @param annualIncome the estimated annual taxable income
     * @return the monthly TDS deduction (annual tax / 12)
     */
    public BigDecimal calculateTds(BigDecimal annualIncome) {
        BigDecimal annualTax = BigDecimal.ZERO;

        if (annualIncome.compareTo(TDS_SLAB_1_LIMIT) <= 0) {
            return BigDecimal.ZERO;
        }

        // Slab 2: ₹2.5L to ₹5L at 5%
        BigDecimal taxableInSlab2 = annualIncome.min(TDS_SLAB_2_LIMIT)
                .subtract(TDS_SLAB_1_LIMIT).max(BigDecimal.ZERO);
        annualTax = annualTax.add(taxableInSlab2.multiply(TDS_RATE_SLAB_2));

        // Slab 3: ₹5L to ₹10L at 20%
        if (annualIncome.compareTo(TDS_SLAB_2_LIMIT) > 0) {
            BigDecimal taxableInSlab3 = annualIncome.min(TDS_SLAB_3_LIMIT)
                    .subtract(TDS_SLAB_2_LIMIT).max(BigDecimal.ZERO);
            annualTax = annualTax.add(
                    taxableInSlab3.multiply(TDS_RATE_SLAB_3));
        }

        // Slab 4: Above ₹10L at 30%
        if (annualIncome.compareTo(TDS_SLAB_3_LIMIT) > 0) {
            BigDecimal taxableInSlab4 = annualIncome
                    .subtract(TDS_SLAB_3_LIMIT);
            annualTax = annualTax.add(
                    taxableInSlab4.multiply(TDS_RATE_SLAB_4));
        }

        // Monthly TDS = annual tax / 12
        return annualTax.divide(MONTHS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates Professional Tax (Maharashtra rules V1).
     *
     * <p>Brackets:</p>
     * <ul>
     *   <li>Gross ≤ ₹7,500: Nil</li>
     *   <li>₹7,501 – ₹10,000: ₹175/month</li>
     *   <li>Above ₹10,000: ₹200/month (₹300 in February)</li>
     * </ul>
     *
     * @param grossSalary the monthly gross salary
     * @param month the calendar month (1-12)
     * @return the PT deduction amount
     */
    public BigDecimal calculatePt(BigDecimal grossSalary, int month) {
        if (grossSalary.compareTo(PT_BRACKET_1_LIMIT) <= 0) {
            return BigDecimal.ZERO;
        }

        if (grossSalary.compareTo(PT_BRACKET_2_LIMIT) <= 0) {
            return PT_BRACKET_2_AMOUNT;
        }

        // Above ₹10,000: ₹200 normally, ₹300 in February (month = 2)
        if (month == 2) {
            return PT_FEBRUARY_AMOUNT;
        }
        return PT_BRACKET_3_AMOUNT;
    }
}
