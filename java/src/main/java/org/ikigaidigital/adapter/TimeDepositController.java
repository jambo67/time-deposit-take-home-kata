package org.ikigaidigital.adapter;

import java.util.List;
import org.ikigaidigital.application.DepositDetails;
import org.ikigaidigital.application.TimeDepositService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/time-deposits")
@CrossOrigin(origins = "http://localhost:8081")
public class TimeDepositController {

  private final TimeDepositService service;

  public TimeDepositController(TimeDepositService service) {
    this.service = service;
  }

  @GetMapping
  public List<DepositDetails> findAll() {
    return service.findAll();
  }

  @PostMapping("/balance-updates")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateBalances() {
    service.updateBalances();
  }
}
