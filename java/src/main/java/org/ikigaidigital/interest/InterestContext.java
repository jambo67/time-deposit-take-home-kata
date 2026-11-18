package org.ikigaidigital.interest;

/**
 * Immutable opening state available to policy selection, eligibility, and calculation.
 */
public record InterestContext(
    int id, String planType, double openingBalance, int days) {

}
