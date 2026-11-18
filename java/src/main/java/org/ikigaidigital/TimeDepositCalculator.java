package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.ikigaidigital.adjustment.BalanceAdjustment;
import org.ikigaidigital.adjustment.BalanceContext;
import org.ikigaidigital.interest.AnnualRateInterestPolicy;
import org.ikigaidigital.interest.InterestPolicy;

public class TimeDepositCalculator {

  private final Map<String, InterestPolicy> policies;
  private final List<BalanceAdjustment> adjustments;

  public TimeDepositCalculator() {
    this(List.of());
  }

  public TimeDepositCalculator(List<BalanceAdjustment> adjustments) {
    this(Map.of(
        "basic", new AnnualRateInterestPolicy(0.01, 30, Integer.MAX_VALUE),
        "student", new AnnualRateInterestPolicy(0.03, 30, 365),
        "premium", new AnnualRateInterestPolicy(0.05, 45, Integer.MAX_VALUE)), adjustments);
  }

  public TimeDepositCalculator(Map<String, InterestPolicy> policies) {
    this(policies, List.of());
  }

  public TimeDepositCalculator(
      Map<String, InterestPolicy> policies, List<BalanceAdjustment> adjustments) {
    this.policies = Map.copyOf(policies);
    this.adjustments = List.copyOf(adjustments);
  }

  public void updateBalance(List<TimeDeposit> deposits) {
    for (TimeDeposit deposit : deposits) {
      double openingBalance = deposit.getBalance();
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
      double closingBalance = openingBalance + roundedInterest;
      if (!adjustments.isEmpty()) {
        BalanceContext context = new BalanceContext(deposit.getId(), deposit.getPlanType(),
            BigDecimal.valueOf(openingBalance), deposit.getDays(),
            BigDecimal.valueOf(roundedInterest));
        BigDecimal adjustmentTotal = BigDecimal.ZERO;
        for (BalanceAdjustment adjustment : adjustments) {
          if (adjustment.appliesTo(context)) {
            adjustmentTotal = adjustmentTotal.add(adjustment.calculateAmount(context));
          }
        }
        closingBalance = BigDecimal.valueOf(closingBalance).add(adjustmentTotal).doubleValue();
      }
      deposit.setBalance(closingBalance);
    }
  }
}
