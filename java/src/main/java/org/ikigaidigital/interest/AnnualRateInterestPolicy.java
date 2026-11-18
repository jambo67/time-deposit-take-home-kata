package org.ikigaidigital.interest;

public record AnnualRateInterestPolicy(
    double annualRate, int startsAfterDays, int lastEligibleDay) implements InterestPolicy {

  @Override
  public double calculateInterest(InterestContext context) {
    if (context.days() <= startsAfterDays || context.days() > lastEligibleDay) {
      return 0;
    }
    // The supplied calculator divides these rates by 12 for one monthly installment.
    return context.openingBalance() * annualRate / 12;
  }
}
