package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.ikigaidigital.adjustment.BalanceAdjustment;
import org.ikigaidigital.adjustment.ConditionalBalanceAdjustment;
import org.ikigaidigital.application.TimeDepositService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(BalanceAdjustmentIntegrationTest.AdjustmentRules.class)
@Testcontainers
class BalanceAdjustmentIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private TimeDepositService service;

  @BeforeEach
  void clearIsolatedTestDatabase() {
    jdbc.update("DELETE FROM withdrawals");
    jdbc.update("DELETE FROM \"timeDeposits\"");
  }

  @Test
  void automaticallyAppliesRegisteredBeansAndPersistsTheCombinedBalances() {
    jdbc.update("""
        INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES
        (1, 'basic', 1200.00, 31), (2, 'basic', 1200.00, 30),
        (3, 'student', 500.00, 366), (4, 'premium', 2500.00, 45)
        """);

    service.updateBalances();

    assertThat(jdbc.queryForList("SELECT balance FROM \"timeDeposits\" ORDER BY id",
        BigDecimal.class)).containsExactly(new BigDecimal("1209.00"),
        new BigDecimal("1208.00"), new BigDecimal("498.00"), new BigDecimal("2510.00"));
    assertThat(jdbc.queryForList("SELECT days FROM \"timeDeposits\" ORDER BY id", Integer.class))
        .containsExactly(31, 30, 366, 45);
  }

  @Test
  void rollsBackEarlierBatchesIfALaterAdjustmentFails() {
    jdbc.update("""
        INSERT INTO "timeDeposits" (id, "planType", balance, days)
        SELECT id, 'basic', 1200.00, 31 FROM generate_series(1, 501) AS id
        """);

    assertThatThrownBy(service::updateBalances)
        .isInstanceOf(IllegalStateException.class).hasMessage("Adjustment failed");

    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM \"timeDeposits\" WHERE balance = 1200.00", Integer.class))
        .isEqualTo(501);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AdjustmentRules {

    @Bean
    BalanceAdjustment promotionForHigherBalances() {
      return new ConditionalBalanceAdjustment(
          context -> context.openingBalance().compareTo(new BigDecimal("1000")) > 0,
          context -> new BigDecimal("10.00"));
    }

    @Bean
    BalanceAdjustment feeForLowerBalances() {
      return new ConditionalBalanceAdjustment(
          context -> context.openingBalance().compareTo(new BigDecimal("2000")) < 0,
          context -> new BigDecimal("-2.00"));
    }

    @Bean
    BalanceAdjustment failureForRollbackTest() {
      return new ConditionalBalanceAdjustment(context -> context.id() == 501, context -> {
        throw new IllegalStateException("Adjustment failed");
      });
    }
  }
}
