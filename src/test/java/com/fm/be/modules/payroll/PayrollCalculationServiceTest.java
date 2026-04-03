package com.fm.be.modules.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PayrollCalculationService}.
 * Validates Indian statutory compliance: PF, ESIC, TDS, and PT calculations.
 */
class PayrollCalculationServiceTest {

    private final PayrollCalculationService calculator =
            new PayrollCalculationService();

    @Nested
    @DisplayName("calculatePF")
    class PfTests {

        @Test
        @DisplayName("PF below cap: 12% of basic")
        void calculatePf_belowCap_returns12Percent() {
            BigDecimal result = calculator.calculatePfEmployee(
                    new BigDecimal("10000"));
            // 10,000 * 0.12 = 1,200
            assertThat(result).isEqualByComparingTo(new BigDecimal("1200.00"));
        }

        @Test
        @DisplayName("PF above cap: 12% of ₹15,000 cap")
        void calculatePf_aboveCap_cappedAt15K() {
            BigDecimal result = calculator.calculatePfEmployee(
                    new BigDecimal("25000"));
            // Capped at 15,000 * 0.12 = 1,800
            assertThat(result).isEqualByComparingTo(new BigDecimal("1800.00"));
        }

        @Test
        @DisplayName("PF exactly at cap")
        void calculatePf_exactlyCap_returnsCapAmount() {
            BigDecimal result = calculator.calculatePfEmployee(
                    new BigDecimal("15000"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("1800.00"));
        }

        @Test
        @DisplayName("PF employer equals employee")
        void calculatePfEmployer_matchesEmployee() {
            BigDecimal empPf = calculator.calculatePfEmployee(
                    new BigDecimal("12000"));
            BigDecimal erPf = calculator.calculatePfEmployer(
                    new BigDecimal("12000"));
            assertThat(empPf).isEqualByComparingTo(erPf);
        }
    }

    @Nested
    @DisplayName("calculateESIC")
    class EsicTests {

        @Test
        @DisplayName("ESIC eligible: gross ≤ ₹21,000")
        void calculateEsic_eligible_returnsRate() {
            BigDecimal empEsic = calculator.calculateEsicEmployee(
                    new BigDecimal("18000"));
            // 18,000 * 0.0075 = 135
            assertThat(empEsic).isEqualByComparingTo(new BigDecimal("135.00"));

            BigDecimal erEsic = calculator.calculateEsicEmployer(
                    new BigDecimal("18000"));
            // 18,000 * 0.0325 = 585
            assertThat(erEsic).isEqualByComparingTo(new BigDecimal("585.00"));
        }

        @Test
        @DisplayName("ESIC ineligible: gross > ₹21,000")
        void calculateEsic_ineligible_returnsZero() {
            BigDecimal empEsic = calculator.calculateEsicEmployee(
                    new BigDecimal("25000"));
            assertThat(empEsic).isEqualByComparingTo(BigDecimal.ZERO);

            BigDecimal erEsic = calculator.calculateEsicEmployer(
                    new BigDecimal("25000"));
            assertThat(erEsic).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("ESIC at threshold boundary: exactly ₹21,000")
        void calculateEsic_exactThreshold_eligible() {
            BigDecimal empEsic = calculator.calculateEsicEmployee(
                    new BigDecimal("21000"));
            // 21,000 * 0.0075 = 157.50
            assertThat(empEsic).isEqualByComparingTo(
                    new BigDecimal("157.50"));
        }
    }

    @Nested
    @DisplayName("calculateTDS")
    class TdsTests {

        @Test
        @DisplayName("TDS nil: income ≤ ₹2.5L")
        void calculateTds_belowSlab1_returnsZero() {
            BigDecimal result = calculator.calculateTds(
                    new BigDecimal("250000"));
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("TDS slab 2: income ₹4L (5% on ₹1.5L)")
        void calculateTds_inSlab2_returns5Percent() {
            BigDecimal result = calculator.calculateTds(
                    new BigDecimal("400000"));
            // (400000 - 250000) * 0.05 = 7500 / 12 = 625
            assertThat(result).isEqualByComparingTo(new BigDecimal("625.00"));
        }

        @Test
        @DisplayName("TDS slab 3: income ₹7L")
        void calculateTds_inSlab3_returns20Percent() {
            BigDecimal result = calculator.calculateTds(
                    new BigDecimal("700000"));
            // Slab 2: 250000 * 0.05 = 12500
            // Slab 3: 200000 * 0.20 = 40000
            // Total: 52500 / 12 = 4375
            assertThat(result).isEqualByComparingTo(new BigDecimal("4375.00"));
        }

        @Test
        @DisplayName("TDS slab 4: income ₹15L")
        void calculateTds_inSlab4_returns30Percent() {
            BigDecimal result = calculator.calculateTds(
                    new BigDecimal("1500000"));
            // Slab 2: 250000 * 0.05 = 12500
            // Slab 3: 500000 * 0.20 = 100000
            // Slab 4: 500000 * 0.30 = 150000
            // Total: 262500 / 12 = 21875
            assertThat(result).isEqualByComparingTo(new BigDecimal("21875.00"));
        }

        @Test
        @DisplayName("TDS at slab 2 boundary: exactly ₹5L")
        void calculateTds_exactSlab2Boundary_correctTax() {
            BigDecimal result = calculator.calculateTds(
                    new BigDecimal("500000"));
            // 250000 * 0.05 = 12500 / 12 = 1041.67
            assertThat(result).isEqualByComparingTo(
                    new BigDecimal("1041.67"));
        }
    }

    @Nested
    @DisplayName("calculatePT")
    class PtTests {

        @Test
        @DisplayName("PT nil: gross ≤ ₹7,500")
        void calculatePt_belowBracket1_returnsZero() {
            BigDecimal result = calculator.calculatePt(
                    new BigDecimal("7500"), 3);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("PT ₹175: gross ₹7,501-₹10,000")
        void calculatePt_bracket2_returns175() {
            BigDecimal result = calculator.calculatePt(
                    new BigDecimal("9000"), 3);
            assertThat(result).isEqualByComparingTo(new BigDecimal("175"));
        }

        @Test
        @DisplayName("PT ₹200: gross > ₹10,000 (non-February)")
        void calculatePt_bracket3_nonFeb_returns200() {
            BigDecimal result = calculator.calculatePt(
                    new BigDecimal("15000"), 6);
            assertThat(result).isEqualByComparingTo(new BigDecimal("200"));
        }

        @Test
        @DisplayName("PT ₹300 in February: gross > ₹10,000")
        void calculatePt_bracket3_february_returns300() {
            BigDecimal result = calculator.calculatePt(
                    new BigDecimal("15000"), 2);
            assertThat(result).isEqualByComparingTo(new BigDecimal("300"));
        }

        @Test
        @DisplayName("PT at bracket 2 boundary: exactly ₹10,000")
        void calculatePt_exactBracket2Boundary_returns175() {
            BigDecimal result = calculator.calculatePt(
                    new BigDecimal("10000"), 3);
            assertThat(result).isEqualByComparingTo(new BigDecimal("175"));
        }
    }
}
