package org.ikigaidigital.adjustment;

import java.math.BigDecimal;

/**
 * Immutable inputs shared by all adjustments for one deposit update.
 */
public record BalanceContext(
    int id, String planType, BigDecimal openingBalance, int days, BigDecimal baseInterest) {

}
