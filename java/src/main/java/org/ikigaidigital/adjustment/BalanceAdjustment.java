package org.ikigaidigital.adjustment;

import java.math.BigDecimal;

public interface BalanceAdjustment {

  boolean appliesTo(BalanceContext context);

  /**
   * Returns a signed decimal credit or deduction, with any rule-specific rounding applied.
   */
  BigDecimal calculateAmount(BalanceContext context);
}
