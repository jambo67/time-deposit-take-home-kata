package org.ikigaidigital.adjustment;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.function.Predicate;

public record ConditionalBalanceAdjustment(
    Predicate<BalanceContext> condition,
    Function<BalanceContext, BigDecimal> calculation) implements BalanceAdjustment {

  @Override
  public boolean appliesTo(BalanceContext context) {
    return condition.test(context);
  }

  @Override
  public BigDecimal calculateAmount(BalanceContext context) {
    return calculation.apply(context);
  }
}
