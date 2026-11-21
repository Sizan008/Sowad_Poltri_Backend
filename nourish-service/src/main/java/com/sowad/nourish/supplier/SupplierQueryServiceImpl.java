package com.sowad.nourish.supplier;

import com.sowad.nourish.contact.repository.JdbcContactQueryRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class SupplierQueryServiceImpl implements SupplierQueryService {
  private final JdbcContactQueryRepository repository;
  private final com.sowad.nourish.contact.PartyDetailsQuery partyDetails;

  public SupplierQueryServiceImpl(
      JdbcContactQueryRepository repository,
      com.sowad.nourish.contact.PartyDetailsQuery partyDetails) {
    this.repository = repository;
    this.partyDetails = partyDetails;
  }

  public List<Map<String, Object>> list(
      String q, String status, Boolean archived, Integer limit, int offset) {
    return enrich(
        repository.list(
            JdbcContactQueryRepository.Type.SUPPLIER, q, status, archived, limit, offset));
  }

  public Map<String, Object> retrieve(String id) {
    return enrich(List.of(repository.retrieve(JdbcContactQueryRepository.Type.SUPPLIER, id)))
        .getFirst();
  }

  public Map<String, Object> details(String id) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("supplier", retrieve(id));
    details.put("transactionSummaryAvailable", true);
    details.put("transactionSummary", partyDetails.supplier(id));
    return details;
  }

  private List<Map<String, Object>> enrich(List<Map<String, Object>> rows) {
    return rows;
  }
}
