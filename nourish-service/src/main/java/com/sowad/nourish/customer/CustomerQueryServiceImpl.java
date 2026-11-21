package com.sowad.nourish.customer;

import com.sowad.nourish.contact.repository.JdbcContactQueryRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class CustomerQueryServiceImpl implements CustomerQueryService {
  private final JdbcContactQueryRepository repository;
  private final com.sowad.nourish.contact.PartyDetailsQuery partyDetails;
  private final CustomerFinancialQuery financial;
  private final com.sowad.nourish.customerrate.repository.JdbcCustomerRateQueryRepository rates;

  public CustomerQueryServiceImpl(
      JdbcContactQueryRepository repository,
      com.sowad.nourish.contact.PartyDetailsQuery partyDetails,
      CustomerFinancialQuery financial,
      com.sowad.nourish.customerrate.repository.JdbcCustomerRateQueryRepository rates) {
    this.repository = repository;
    this.partyDetails = partyDetails;
    this.financial = financial;
    this.rates = rates;
  }

  public List<Map<String, Object>> list(
      String q, String status, Boolean archived, Integer limit, int offset) {
    return enrich(
        repository.list(
            JdbcContactQueryRepository.Type.CUSTOMER, q, status, archived, limit, offset));
  }

  public Map<String, Object> retrieve(String id) {
    return enrich(List.of(repository.retrieve(JdbcContactQueryRepository.Type.CUSTOMER, id)))
        .getFirst();
  }

  public Map<String, Object> details(String id) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("customer", retrieve(id));
    details.put("transactionSummaryAvailable", true);
    details.put("transactionSummary", financial.summary(id));
    details.putAll(partyDetails.customer(id));
    return details;
  }

  private List<Map<String, Object>> enrich(List<Map<String, Object>> rows) {
    var ids = rows.stream().map(r -> r.get("id").toString()).toList();
    var unitRates =
        rates.unitRates(ids).stream()
            .collect(java.util.stream.Collectors.groupingBy(r -> r.get("customerId").toString()));
    var packageRates =
        rates.packageRates(ids).stream()
            .collect(java.util.stream.Collectors.groupingBy(r -> r.get("customerId").toString()));
    for (var row : rows) {
      String id = row.get("id").toString();
      row.put("customerRates", unitRates.getOrDefault(id, List.of()));
      row.put("packageRates", packageRates.getOrDefault(id, List.of()));
    }
    return rows;
  }
}
