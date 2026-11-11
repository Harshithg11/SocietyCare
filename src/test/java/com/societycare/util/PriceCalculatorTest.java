package com.societycare.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculatorTest {

    @Test
    void twentyPercentOffThousandIsEightHundred() {
        BigDecimal result = PriceCalculator.discountedPrice(new BigDecimal("1000.00"), new BigDecimal("20"));
        assertThat(result).isEqualByComparingTo("800.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void roundsToTwoDecimalsHalfUp() {
        // 999.99 * 12.5 / 100 = 124.99875 -> 125.00 ; 999.99 - 125.00 = 874.99
        BigDecimal result = PriceCalculator.discountedPrice(new BigDecimal("999.99"), new BigDecimal("12.5"));
        assertThat(result).isEqualByComparingTo("874.99");
    }
}
