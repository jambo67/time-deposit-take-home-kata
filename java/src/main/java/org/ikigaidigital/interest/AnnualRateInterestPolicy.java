package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

public record AnnualRateInterestPolicy(
    double annualRate, int startsAfterDays, int lastEligibleDay) implements InterestPolicy {

  @Override
  public double calculateInterest(TimeDeposit deposit) {
    if (deposit.getDays() <= startsAfterDays || deposit.getDays() > lastEligibleDay) {
      return 0;
    }
    // The supplied calculator divides these rates by 12 for one monthly installment.
    return deposit.getBalance() * annualRate / 12;
  }
}
