package com.societycare.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PriceCalculator() {
    }

    /** Discounted Price = Regular Price - (Regular Price x Discount Percentage / 100), rounded to 2 decimals. */
    public static BigDecimal discountedPrice(BigDecimal regularPrice, BigDecimal discountPercentage) {
        BigDecimal discount = regularPrice.multiply(discountPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return regularPrice.subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }
}
