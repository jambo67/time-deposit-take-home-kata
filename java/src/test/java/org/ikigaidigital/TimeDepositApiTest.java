package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.ikigaidigital.application.TimeDepositService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimeDepositApiTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort
  private int port;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private TimeDepositService service;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private RequestMappingHandlerMapping mappings;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void clearIsolatedTestDatabase() {
    jdbc.update("DELETE FROM withdrawals");
    jdbc.update("DELETE FROM \"timeDeposits\"");
  }

  @Test
  void returnsAnEmptyArrayAndAcceptsAnUpdateWhenNoDepositsExist() throws Exception {
    HttpResponse<String> response = getDeposits();

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("access-control-allow-origin"))
        .hasValue("http://localhost:8081");
    assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(
        value -> assertThat(value).startsWith("application/json"));
    assertThat(json.readTree(response.body()).isArray()).isTrue();
    assertThat(json.readTree(response.body()).size()).isZero();
    assertThat(postUpdate().statusCode()).isEqualTo(204);
  }

  @Test
  void returnsAllRequiredFieldsAndOrderedWithdrawalsWithoutOmittingEmptyCollections()
      throws Exception {
    insertDeposit(2, "premium", "1200.00", 46);
    insertDeposit(1, "basic", "1200.00", 31);
    jdbc.update("""
        INSERT INTO withdrawals (id, "timeDepositId", amount, date) VALUES
        (2, 1, 20.50, '2026-02-01'), (1, 1, 10.25, '2026-01-01')
        """);

    HttpResponse<String> response = getDeposits();
    JsonNode deposits = json.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(deposits.size()).isEqualTo(2);
    JsonNode first = deposits.get(0);
    assertThat(first.size()).isEqualTo(5);
    assertThat(first.get("id").asInt()).isEqualTo(1);
    assertThat(first.get("planType").asText()).isEqualTo("basic");
    assertThat(first.get("balance").decimalValue()).isEqualByComparingTo("1200.00");
    assertThat(first.get("days").asInt()).isEqualTo(31);
    assertThat(first.get("withdrawals").size()).isEqualTo(2);
    JsonNode withdrawal = first.get("withdrawals").get(0);
    assertThat(withdrawal.get("id").asInt()).isEqualTo(1);
    assertThat(withdrawal.get("timeDepositId").asInt()).isEqualTo(1);
    assertThat(withdrawal.get("amount").decimalValue()).isEqualByComparingTo("10.25");
    assertThat(withdrawal.get("date").asText()).isEqualTo("2026-01-01");
    assertThat(deposits.get(1).get("id").asInt()).isEqualTo(2);
    assertThat(deposits.get(1).get("withdrawals").isArray()).isTrue();
    assertThat(deposits.get(1).get("withdrawals").size()).isZero();
  }

  @Test
  void updatesEveryEligiblePlanAndPreservesDaysAndWithdrawalHistory() throws Exception {
    insertDeposit(1, "basic", "1200.00", 31);
    insertDeposit(2, "student", "1200.00", 365);
    insertDeposit(3, "student", "1200.00", 366);
    insertDeposit(4, "premium", "1200.00", 45);
    insertDeposit(5, "premium", "1200.00", 46);
    insertDeposit(6, "basic", "1200.00", 30);
    jdbc.update("""
        INSERT INTO withdrawals (id, "timeDepositId", amount, date)
        VALUES (1, 1, 50.00, '2026-01-01')
        """);

    HttpResponse<String> response = postUpdate();

    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(response.body()).isEmpty();
    assertThat(jdbc.queryForList("SELECT balance FROM \"timeDeposits\" ORDER BY id",
        BigDecimal.class)).containsExactly(
        new BigDecimal("1201.00"), new BigDecimal("1203.00"), new BigDecimal("1200.00"),
        new BigDecimal("1200.00"), new BigDecimal("1205.00"), new BigDecimal("1200.00"));
    assertThat(jdbc.queryForList("SELECT days FROM \"timeDeposits\" ORDER BY id", Integer.class))
        .containsExactly(31, 365, 366, 45, 46, 30);
    JsonNode first = json.readTree(getDeposits().body()).get(0);
    assertThat(first.get("balance").decimalValue()).isEqualByComparingTo("1201.00");
    assertThat(first.get("withdrawals").get(0).get("amount").decimalValue())
        .isEqualByComparingTo("50.00");
  }

  @Test
  void processesMoreThanOneBatchExactlyOnce() throws Exception {
    insertBasicDeposits(501);

    assertThat(postUpdate().statusCode()).isEqualTo(204);

    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM \"timeDeposits\" WHERE balance = 1201.00", Integer.class))
        .isEqualTo(501);
  }

  @Test
  void rollsBackEarlierBatchesWhenALaterWriteFails() {
    insertBasicDeposits(500);
    insertDeposit(501, "basic", "99999999999999999.99", 31);

    assertThatThrownBy(service::updateBalances).isInstanceOf(DataAccessException.class);

    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM \"timeDeposits\" WHERE balance = 1200.00", Integer.class))
        .isEqualTo(500);
    assertThat(balance(501)).isEqualByComparingTo("99999999999999999.99");
  }

  @Test
  void concurrentRequestsWaitForDatabaseLocksAndDoNotLoseAnInstallment() throws Exception {
    insertDeposit(1, "basic", "1200.00", 31);
    var workers = Executors.newFixedThreadPool(2);
    try (Connection blocker = dataSource.getConnection()) {
      blocker.setAutoCommit(false);
      try (Statement statement = blocker.createStatement();
          var rows = statement.executeQuery(
              "SELECT id FROM \"timeDeposits\" WHERE id = 1 FOR UPDATE")) {
        assertThat(rows.next()).isTrue();
      }

      var first = workers.submit(this::postUpdate);
      var second = workers.submit(this::postUpdate);

      // Observe both transactions waiting in PostgreSQL instead of assuming a sleep is enough.
      await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
          assertThat(jdbc.queryForObject("""
              SELECT COUNT(*) FROM pg_stat_activity
              WHERE datname = current_database() AND wait_event_type = 'Lock'
              """, Integer.class)).isGreaterThanOrEqualTo(2));
      blocker.commit();

      assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(204);
      assertThat(second.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(204);
      assertThat(balance(1)).isEqualByComparingTo("1202.00");
    } finally {
      workers.shutdownNow();
      workers.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void databaseRejectsOrphanedWithdrawals() {
    assertThatThrownBy(() -> jdbc.update("""
        INSERT INTO withdrawals (id, "timeDepositId", amount, date)
        VALUES (1, 999, 10.00, '2026-01-01')
        """)).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void exposesExactlyTheTwoChallengeOperations() {
    Set<String> operations = mappings.getHandlerMethods().entrySet().stream()
        .filter(entry -> entry.getValue().getBeanType().getPackageName()
            .startsWith("org.ikigaidigital"))
        .flatMap(entry -> entry.getKey().getPatternValues().stream()
            .flatMap(path -> entry.getKey().getMethodsCondition().getMethods().stream()
                .map(method -> method.name() + " " + path)))
        .collect(Collectors.toSet());

    assertThat(operations).containsExactlyInAnyOrder(
        "GET /time-deposits", "POST /time-deposits/balance-updates");
  }

  private void insertDeposit(int id, String plan, String balance, int days) {
    jdbc.update("""
        INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES (?, ?, ?, ?)
        """, id, plan, new BigDecimal(balance), days);
  }

  private void insertBasicDeposits(int count) {
    jdbc.update("""
        INSERT INTO "timeDeposits" (id, "planType", balance, days)
        SELECT id, 'basic', 1200.00, 31 FROM generate_series(1, ?) AS id
        """, count);
  }

  private BigDecimal balance(int id) {
    return jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = ?",
        BigDecimal.class, id);
  }

  private HttpResponse<String> getDeposits() throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/time-deposits"))
        .header("Origin", "http://localhost:8081")
        .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postUpdate() throws Exception {
    return http.send(HttpRequest.newBuilder(
            URI.create(baseUrl() + "/time-deposits/balance-updates"))
        .timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }
}
