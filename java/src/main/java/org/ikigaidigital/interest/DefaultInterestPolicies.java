package org.ikigaidigital.interest;

import java.util.Map;

/**
 * Default policies shared by Spring configuration and standalone calculator callers.
 */
public final class DefaultInterestPolicies {

  public static final AnnualRateInterestPolicy BASIC =
      new AnnualRateInterestPolicy(0.01, 30, Integer.MAX_VALUE);
  public static final AnnualRateInterestPolicy STUDENT =
      new AnnualRateInterestPolicy(0.03, 30, 365);
  public static final AnnualRateInterestPolicy PREMIUM =
      new AnnualRateInterestPolicy(0.05, 45, Integer.MAX_VALUE);

  private DefaultInterestPolicies() {
  }

  public static Map<String, InterestPolicy> asMap() {
    return Map.of("basic", BASIC, "student", STUDENT, "premium", PREMIUM);
  }
}
