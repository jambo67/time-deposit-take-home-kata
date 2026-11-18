package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.ikigaidigital.configuration.InterestConfiguration;
import org.ikigaidigital.interest.ConditionalInterestPolicy;
import org.ikigaidigital.interest.InterestPolicy;
import org.ikigaidigital.interest.InterestPolicyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class InterestConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(InterestConfiguration.class);

  @Test
  void usesNamedPolicyBeansAndPreservesDefaultsWithoutAdjustments() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context.getBeansOfType(InterestPolicy.class))
          .containsOnlyKeys("basic", "student", "premium");
      List<TimeDeposit> deposits = List.of(
          new TimeDeposit(1, "basic", 1200.0, 30),
          new TimeDeposit(2, "basic", 1200.0, 31),
          new TimeDeposit(3, "student", 1200.0, 365),
          new TimeDeposit(4, "student", 1200.0, 366),
          new TimeDeposit(5, "premium", 1200.0, 45),
          new TimeDeposit(6, "premium", 1200.0, 46));

      context.getBean(TimeDepositCalculator.class).updateBalance(deposits);

      assertThat(deposits).extracting(TimeDeposit::getBalance)
          .containsExactly(1200.0, 1201.0, 1203.0, 1200.0, 1200.0, 1205.0);
    });
  }

  @Test
  void configuresRateAndBothDayBoundariesWithoutEditingTheCalculator() {
    contextRunner.withPropertyValues(
        "interest.basic.annual-rate=0.06",
        "interest.basic.starts-after-days=10",
        "interest.basic.last-eligible-day=20").run(context -> {
          assertThat(context).hasNotFailed();
          List<TimeDeposit> deposits = List.of(
              new TimeDeposit(1, "basic", 1200.0, 10),
              new TimeDeposit(2, "basic", 1200.0, 11),
              new TimeDeposit(3, "basic", 1200.0, 20),
              new TimeDeposit(4, "basic", 1200.0, 21),
              new TimeDeposit(5, "student", 1200.0, 31));

          context.getBean(TimeDepositCalculator.class).updateBalance(deposits);

          assertThat(deposits).extracting(TimeDeposit::getBalance)
              .containsExactly(1200.0, 1206.0, 1206.0, 1200.0, 1203.0);
        });
  }

  @Test
  void changingOnlyTheCutoffRetainsTheOtherPlanSettings() {
    contextRunner.withPropertyValues("interest.basic.starts-after-days=10").run(context -> {
      assertThat(context).hasNotFailed();
      List<TimeDeposit> deposits = List.of(
          new TimeDeposit(1, "basic", 1200.0, 11),
          new TimeDeposit(2, "basic", 1200.0, 366));

      context.getBean(TimeDepositCalculator.class).updateBalance(deposits);

      assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1201.0, 1201.0);
    });
  }

  @Test
  void discoversAnAdditionalPolicyBeanWithItsOwnEligibilityCriteria() {
    contextRunner.withUserConfiguration(AdditionalPlan.class).run(context -> {
      assertThat(context).hasNotFailed();
      List<TimeDeposit> deposits = List.of(
          new TimeDeposit(1, "new-plan", 1200.0, 1),
          new TimeDeposit(2, "new-plan", 500.0, 1),
          new TimeDeposit(3, "unknown", 1200.0, 31));

      context.getBean(TimeDepositCalculator.class).updateBalance(deposits);

      assertThat(deposits).extracting(TimeDeposit::getBalance)
          .containsExactly(1210.0, 500.0, 1200.0);
    });
  }

  @Test
  void allowsPolicySelectionToBeReplacedWithoutChangingTheCalculator() {
    InterestPolicy policy = context -> context.openingBalance() * 0.01;
    contextRunner.withBean(InterestPolicyProvider.class,
        () -> context -> context.id() == 1 ? policy : null).run(context -> {
      assertThat(context).hasNotFailed();
      List<TimeDeposit> deposits = List.of(
          new TimeDeposit(1, "basic", 1200.0, 31),
          new TimeDeposit(2, "student", 1200.0, 31));

      context.getBean(TimeDepositCalculator.class).updateBalance(deposits);

      assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1212.0, 1200.0);
    });
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AdditionalPlan {

    @Bean("new-plan")
    InterestPolicy balanceBasedInterest() {
      return new ConditionalInterestPolicy(
          context -> context.openingBalance() >= 1000,
          context -> 10.0);
    }
  }
}
