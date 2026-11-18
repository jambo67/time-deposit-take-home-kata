package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

@FunctionalInterface
public interface InterestPolicy {

  double calculateInterest(TimeDeposit deposit);
}
