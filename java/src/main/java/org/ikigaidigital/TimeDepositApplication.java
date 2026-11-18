package org.ikigaidigital;

import org.ikigaidigital.adjustment.BalanceAdjustment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TimeDepositApplication {

  public static void main(String[] args) {
    SpringApplication.run(TimeDepositApplication.class, args);
  }

  @Bean
  TimeDepositCalculator timeDepositCalculator(ObjectProvider<BalanceAdjustment> adjustments) {
    return new TimeDepositCalculator(adjustments.stream().toList());
  }
}
