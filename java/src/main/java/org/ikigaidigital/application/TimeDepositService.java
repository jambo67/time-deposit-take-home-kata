package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeDepositService {

  private static final int BATCH_SIZE = 500;

  private final TimeDepositRepository repository;
  private final TimeDepositCalculator calculator;

  public TimeDepositService(
      TimeDepositRepository repository, TimeDepositCalculator calculator) {
    this.repository = repository;
    this.calculator = calculator;
  }

  @Transactional(readOnly = true)
  public List<DepositDetails> findAll() {
    return repository.findAll();
  }

  @Transactional
  public void updateBalances() {
    // Each call applies one installment; the challenge specifies no calendar-period deduplication.
    Integer upperId = repository.highestId();
    if (upperId == null) {
      return;
    }
    long lastId = Long.MIN_VALUE;
    while (true) {
      List<TimeDeposit> deposits = repository.lockBatchAfter(lastId, upperId, BATCH_SIZE);
      if (deposits.isEmpty()) {
        return;
      }
      calculator.updateBalance(deposits);
      repository.saveBalances(deposits);
      lastId = deposits.get(deposits.size() - 1).getId();
    }
  }
}
