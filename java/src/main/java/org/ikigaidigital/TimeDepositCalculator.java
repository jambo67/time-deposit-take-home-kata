package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.ikigaidigital.adjustment.BalanceAdjustment;
import org.ikigaidigital.adjustment.BalanceContext;
import org.ikigaidigital.interest.DefaultInterestPolicies;
import org.ikigaidigital.interest.InterestContext;
import org.ikigaidigital.interest.InterestPolicy;
import org.ikigaidigital.interest.InterestPolicyProvider;

public class TimeDepositCalculator {

  private final InterestPolicyProvider policies;
  private final List<BalanceAdjustment> adjustments;

  public TimeDepositCalculator() {
    this(List.of());
  }

  public TimeDepositCalculator(List<BalanceAdjustment> adjustments) {
    this(DefaultInterestPolicies.asMap(), adjustments);
  }

  public TimeDepositCalculator(Map<String, InterestPolicy> policies) {
    this(policies, List.of());
  }

  public TimeDepositCalculator(
      Map<String, InterestPolicy> policies, List<BalanceAdjustment> adjustments) {
    this(InterestPolicyProvider.byPlan(policies), adjustments);
  }

  public TimeDepositCalculator(
      InterestPolicyProvider policies, List<BalanceAdjustment> adjustments) {
    this.policies = policies;
    this.adjustments = List.copyOf(adjustments);
  }

  public void updateBalance(List<TimeDeposit> deposits) {
    for (TimeDeposit deposit : deposits) {
      double openingBalance = deposit.getBalance();
      InterestContext interestContext = new InterestContext(deposit.getId(), deposit.getPlanType(),
          openingBalance, deposit.getDays());
      InterestPolicy policy = policies.policyFor(interestContext);
      double interest = policy == null ? 0 : policy.calculateInterest(interestContext);
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
