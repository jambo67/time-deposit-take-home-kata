package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.ikigaidigital.interest.AnnualRateInterestPolicy;
import org.ikigaidigital.interest.InterestPolicy;

public class TimeDepositCalculator {

  private final Map<String, InterestPolicy> policies;

  public TimeDepositCalculator() {
    this(Map.of(
        "basic", new AnnualRateInterestPolicy(0.01, 30, Integer.MAX_VALUE),
        "student", new AnnualRateInterestPolicy(0.03, 30, 365),
        "premium", new AnnualRateInterestPolicy(0.05, 45, Integer.MAX_VALUE)));
  }

  public TimeDepositCalculator(Map<String, InterestPolicy> policies) {
    this.policies = Map.copyOf(policies);
  }

  public void updateBalance(List<TimeDeposit> deposits) {
    for (TimeDeposit deposit : deposits) {
      double interest = 0;
      if (deposit.getDays() > 30) {
        InterestPolicy policy = policies.get(deposit.getPlanType());
        if (policy != null) {
          interest = policy.calculateInterest(deposit);
        }
      }
      // Preserve the original double conversion and rounding, including half-cent boundaries.
      double roundedInterest =
          new BigDecimal(interest).setScale(2, RoundingMode.HALF_UP).doubleValue();
      deposit.setBalance(deposit.getBalance() + roundedInterest);
    }
  }
}
