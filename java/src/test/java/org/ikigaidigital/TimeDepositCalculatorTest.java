package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.ikigaidigital.interest.AnnualRateInterestPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeDepositCalculatorTest {

  private final TimeDepositCalculator calculator = new TimeDepositCalculator();

  @ParameterizedTest
  @CsvSource({
      "basic, 29, 1200", "basic, 30, 1200", "basic, 31, 1201",
      "basic, 365, 1201", "basic, 366, 1201",
      "student, 29, 1200", "student, 30, 1200", "student, 31, 1203",
      "student, 365, 1203", "student, 366, 1200",
      "premium, 30, 1200", "premium, 31, 1200", "premium, 45, 1200",
      "premium, 46, 1205", "premium, 366, 1205", "unknown, 46, 1200"
  })
  void appliesOneMonthlyInstallmentAtTheExistingDayBoundaries(
      String plan, int days, double expectedBalance) {
    TimeDeposit deposit = new TimeDeposit(1, plan, 1200.0, days);

    calculator.updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(expectedBalance);
    assertThat(deposit.getId()).isEqualTo(1);
    assertThat(deposit.getPlanType()).isEqualTo(plan);
    assertThat(deposit.getDays()).isEqualTo(days);
  }

  @ParameterizedTest
  @CsvSource({"6.0, 6.01", "18.0, 18.01", "1200.123, 1201.123", "0.0, 0.0"})
  void preservesBinaryDoubleRoundingAndRoundsInterestBeforeAddingIt(
      double balance, double expectedBalance) {
    TimeDeposit deposit = new TimeDeposit(1, "basic", balance, 31);

    calculator.updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(expectedBalance);
  }

  @Test
  void updatesEveryDepositAndAppliesAnotherInstallmentOnEachCall() {
    List<TimeDeposit> deposits = List.of(
        new TimeDeposit(1, "basic", 1200.0, 31),
        new TimeDeposit(2, "student", 1200.0, 365),
        new TimeDeposit(3, "premium", 1200.0, 46));

    calculator.updateBalance(deposits);
    calculator.updateBalance(deposits);

    assertThat(deposits).extracting(TimeDeposit::getBalance)
        .containsExactly(1202.0, 1206.01, 1210.02);
  }

  @Test
  void acceptsAnEmptyList() {
    calculator.updateBalance(List.of());
  }

  @ParameterizedTest
  @CsvSource({"60, 1200", "61, 1206", "180, 1206", "181, 1200"})
  void allowsRatesAndDurationToChangeByRegisteringADifferentPolicy(int days, double expected) {
    TimeDepositCalculator configured = new TimeDepositCalculator(
        Map.of("basic", new AnnualRateInterestPolicy(0.06, 60, 180)));
    TimeDeposit deposit = new TimeDeposit(1, "basic", 1200.0, days);

    configured.updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(expected);
  }

  @Test
  void supportsADifferentCalculationThroughThePolicyInterface() {
    TimeDepositCalculator configured = new TimeDepositCalculator(
        Map.of("new-plan", deposit -> deposit.getBalance() >= 1000 ? 10.0 : 2.0));
    List<TimeDeposit> deposits = List.of(
        new TimeDeposit(1, "new-plan", 1200.0, 31),
        new TimeDeposit(2, "new-plan", 500.0, 31));

    configured.updateBalance(deposits);

    assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1210.0, 502.0);
  }
}
