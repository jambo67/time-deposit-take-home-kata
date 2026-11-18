package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.ikigaidigital.adjustment.BalanceAdjustment;
import org.ikigaidigital.adjustment.BalanceContext;
import org.ikigaidigital.adjustment.ConditionalBalanceAdjustment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BalanceAdjustmentTest {

  @Test
  void stacksAllMatchingCreditsAndDeductionsRegardlessOfRegistrationOrder() {
    BalanceAdjustment promotion = fixed(
        context -> context.openingBalance().compareTo(new BigDecimal("1000")) > 0, "1000.00");
    BalanceAdjustment fee = fixed(
        context -> context.openingBalance().compareTo(new BigDecimal("2000")) < 0, "-50.00");
    BalanceAdjustment ineligible = new ConditionalBalanceAdjustment(
        context -> context.planType().equals("student"),
        context -> {
          throw new AssertionError("An ineligible adjustment must not be calculated");
        });
    TimeDeposit first = new TimeDeposit(1, "basic", 1200.0, 31);
    TimeDeposit second = new TimeDeposit(2, "basic", 1200.0, 31);

    new TimeDepositCalculator(List.of(promotion, fee, ineligible)).updateBalance(List.of(first));
    new TimeDepositCalculator(List.of(ineligible, fee, promotion)).updateBalance(List.of(second));

    assertThat(first.getBalance()).isEqualTo(2151.0);
    assertThat(second.getBalance()).isEqualTo(first.getBalance());
    assertThat(first.getId()).isEqualTo(1);
    assertThat(first.getPlanType()).isEqualTo("basic");
    assertThat(first.getDays()).isEqualTo(31);
  }

  @ParameterizedTest
  @CsvSource({"999.99, 1000.82", "1000.0, 1000.83", "1000.01, 1010.84"})
  void evaluatesAmountThresholdsAgainstTheOpeningBalance(
      double openingBalance, double expectedBalance) {
    BalanceAdjustment promotion = fixed(
        context -> context.openingBalance().compareTo(new BigDecimal("1000")) > 0, "10.00");
    TimeDeposit deposit = new TimeDeposit(1, "basic", openingBalance, 31);

    new TimeDepositCalculator(List.of(promotion)).updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isCloseTo(expectedBalance, within(0.000000001));
  }

  @ParameterizedTest
  @CsvSource({"basic, 30", "student, 366", "premium, 45", "unknown, 31"})
  void letsAdjustmentsDefineTheirOwnEligibilityWhenBaseInterestIsZero(String plan, int days) {
    TimeDeposit deposit = new TimeDeposit(1, plan, 1200.0, days);
    List<BalanceAdjustment> adjustments = List.of(
        fixed(context -> true, "10.00"), fixed(context -> true, "-2.00"));

    new TimeDepositCalculator(adjustments).updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(1208.0);
    assertThat(deposit.getDays()).isEqualTo(days);
  }

  @Test
  void preservesLegacyRoundingWhenRegisteredAdjustmentsDoNotApply() {
    TimeDeposit deposit = new TimeDeposit(1, "basic", 1200.123, 31);

    new TimeDepositCalculator(List.of(fixed(context -> false, "10.00")))
        .updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(1201.123);
  }

  @Test
  void exposesTheAlreadyRoundedBaseInterestToAdjustments() {
    BalanceAdjustment bonus = new ConditionalBalanceAdjustment(
        context -> true,
        context -> context.baseInterest().multiply(new BigDecimal("2")));
    TimeDeposit deposit = new TimeDeposit(1, "basic", 18.0, 31);

    new TimeDepositCalculator(List.of(bonus)).updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(18.03);
  }

  @Test
  void supportsCombinedCriteriaAndCreatesFreshInputsForEachDeposit() {
    BalanceAdjustment promotion = fixed(
        context -> context.planType().equals("student")
            && context.days() >= 180
            && context.openingBalance().compareTo(new BigDecimal("1000")) > 0,
        "5.00");
    List<TimeDeposit> deposits = List.of(
        new TimeDeposit(1, "basic", 1200.0, 200),
        new TimeDeposit(2, "student", 1200.0, 179),
        new TimeDeposit(3, "student", 1200.0, 180));

    new TimeDepositCalculator(List.of(promotion)).updateBalance(deposits);

    assertThat(deposits).extracting(TimeDeposit::getBalance)
        .containsExactly(1201.0, 1203.0, 1208.0);
  }

  @Test
  void takesASnapshotOfRegisteredAdjustments() {
    List<BalanceAdjustment> registrations = new ArrayList<>();
    registrations.add(fixed(context -> true, "10.00"));
    TimeDepositCalculator calculator = new TimeDepositCalculator(registrations);
    registrations.add(fixed(context -> true, "-100.00"));
    TimeDeposit deposit = new TimeDeposit(1, "basic", 1200.0, 31);

    calculator.updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(1211.0);
  }

  @Test
  void addsDecimalCreditsAndDeductionsBeforeConvertingTheTotalBackToDouble() {
    TimeDeposit deposit = new TimeDeposit(1, "basic", 1200.123, 31);
    List<BalanceAdjustment> adjustments = List.of(
        fixed(context -> true, "0.10"),
        fixed(context -> true, "0.20"),
        fixed(context -> true, "-0.30"));

    new TimeDepositCalculator(adjustments).updateBalance(List.of(deposit));

    assertThat(deposit.getBalance()).isEqualTo(1201.123);
  }

  private static BalanceAdjustment fixed(Predicate<BalanceContext> condition, String amount) {
    BigDecimal value = new BigDecimal(amount);
    return new ConditionalBalanceAdjustment(condition, context -> value);
  }
}
