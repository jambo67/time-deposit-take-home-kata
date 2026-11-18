package org.ikigaidigital.interest;

@FunctionalInterface
public interface InterestPolicy {

  double calculateInterest(InterestContext context);
}
