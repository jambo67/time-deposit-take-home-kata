package org.ikigaidigital.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.DepositDetails;
import org.ikigaidigital.application.DepositDetails.Withdrawal;
import org.ikigaidigital.application.TimeDepositRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTimeDepositRepository implements TimeDepositRepository {

  private final JdbcTemplate jdbc;

  public JdbcTimeDepositRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<DepositDetails> findAll() {
    return jdbc.query("""
        SELECT d.id, d."planType", d.balance, d.days,
               w.id AS withdrawal_id, w.amount, w.date
        FROM "timeDeposits" d
        LEFT JOIN withdrawals w ON w."timeDepositId" = d.id
        ORDER BY d.id, w.date, w.id
        """, rows -> {
      Map<Integer, DepositDetails> deposits = new LinkedHashMap<>();
      while (rows.next()) {
        int id = rows.getInt("id");
        DepositDetails deposit = deposits.get(id);
        if (deposit == null) {
          deposit = new DepositDetails(id, rows.getString("planType"),
              rows.getBigDecimal("balance"), rows.getInt("days"), new ArrayList<>());
          deposits.put(id, deposit);
        }
        Integer withdrawalId = rows.getObject("withdrawal_id", Integer.class);
        if (withdrawalId != null) {
          deposit.withdrawals().add(new Withdrawal(withdrawalId, id,
              rows.getBigDecimal("amount"), rows.getDate("date").toLocalDate()));
        }
      }
      return deposits.values().stream()
          .map(deposit -> new DepositDetails(deposit.id(), deposit.planType(),
              deposit.balance(), deposit.days(), List.copyOf(deposit.withdrawals())))
          .toList();
    });
  }

  @Override
  public Integer highestId() {
    return jdbc.queryForObject("SELECT MAX(id) FROM \"timeDeposits\"", Integer.class);
  }

  @Override
  public List<TimeDeposit> lockBatchAfter(long lastId, int upperId, int batchSize) {
    // Lock before calculation; ordered acquisition also reduces deadlock risk between requests.
    return jdbc.query("""
        SELECT id, "planType", balance, days
        FROM "timeDeposits"
        WHERE id > ? AND id <= ?
        ORDER BY id
        LIMIT ?
        FOR UPDATE
        """, (row, index) -> new TimeDeposit(row.getInt("id"), row.getString("planType"),
        row.getBigDecimal("balance").doubleValue(), row.getInt("days")),
        lastId, upperId, batchSize);
  }

  @Override
  public void saveBalances(List<TimeDeposit> deposits) {
    jdbc.batchUpdate("UPDATE \"timeDeposits\" SET balance = ? WHERE id = ?",
        deposits, deposits.size(), (statement, deposit) -> {
          // Persist money as cents, retaining the protected calculator's double-based behavior.
          statement.setBigDecimal(1,
              BigDecimal.valueOf(deposit.getBalance()).setScale(2, RoundingMode.HALF_UP));
          statement.setInt(2, deposit.getId());
        });
  }
}
