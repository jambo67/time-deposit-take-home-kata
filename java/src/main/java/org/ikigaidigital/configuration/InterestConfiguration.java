package org.ikigaidigital.configuration;

import java.util.Map;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.adjustment.BalanceAdjustment;
import org.ikigaidigital.interest.AnnualRateInterestPolicy;
import org.ikigaidigital.interest.DefaultInterestPolicies;
import org.ikigaidigital.interest.InterestPolicy;
import org.ikigaidigital.interest.InterestPolicyProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class InterestConfiguration {

  @Bean("basic")
  InterestPolicy basicInterestPolicy(Environment environment) {
    return configuredPolicy("basic", DefaultInterestPolicies.BASIC, environment);
  }

  @Bean("student")
  InterestPolicy studentInterestPolicy(Environment environment) {
    return configuredPolicy("student", DefaultInterestPolicies.STUDENT, environment);
  }

  @Bean("premium")
  InterestPolicy premiumInterestPolicy(Environment environment) {
    return configuredPolicy("premium", DefaultInterestPolicies.PREMIUM, environment);
  }

  @Bean
  @ConditionalOnMissingBean(InterestPolicyProvider.class)
  InterestPolicyProvider interestPolicyProvider(Map<String, InterestPolicy> policies) {
    return InterestPolicyProvider.byPlan(policies);
  }

  @Bean
  TimeDepositCalculator timeDepositCalculator(
      InterestPolicyProvider policies, ObjectProvider<BalanceAdjustment> adjustments) {
    return new TimeDepositCalculator(policies, adjustments.stream().toList());
  }

  private AnnualRateInterestPolicy configuredPolicy(
      String plan, AnnualRateInterestPolicy defaults, Environment environment) {
    String prefix = "interest." + plan + ".";
    return new AnnualRateInterestPolicy(
        environment.getProperty(prefix + "annual-rate", Double.class, defaults.annualRate()),
        environment.getProperty(prefix + "starts-after-days", Integer.class,
            defaults.startsAfterDays()),
        environment.getProperty(prefix + "last-eligible-day", Integer.class,
            defaults.lastEligibleDay()));
  }
}
