package org.ikigaidigital.interest;

import java.util.function.Predicate;

public record ConditionalInterestPolicy(
    Predicate<InterestContext> condition, InterestPolicy calculation) implements InterestPolicy {

  @Override
  public double calculateInterest(InterestContext context) {
    return condition.test(context) ? calculation.calculateInterest(context) : 0;
  }
}
