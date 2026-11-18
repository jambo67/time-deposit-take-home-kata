package org.ikigaidigital.interest;

import java.util.Map;

/**
 * Selects a calculation strategy without coupling the calculator to selection criteria.
 */
@FunctionalInterface
public interface InterestPolicyProvider {

  InterestPolicy policyFor(InterestContext context);

  static InterestPolicyProvider byPlan(Map<String, InterestPolicy> policies) {
    Map<String, InterestPolicy> registrations = Map.copyOf(policies);
    return context -> registrations.get(context.planType());
  }
}
