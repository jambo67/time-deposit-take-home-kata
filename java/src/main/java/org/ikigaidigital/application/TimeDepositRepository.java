package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDeposit;

public interface TimeDepositRepository {

  List<DepositDetails> findAll();

  Integer highestId();

  List<TimeDeposit> lockBatchAfter(long lastId, int upperId, int batchSize);

  void saveBalances(List<TimeDeposit> deposits);
}
