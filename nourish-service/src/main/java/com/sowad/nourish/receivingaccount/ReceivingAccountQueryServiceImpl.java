package com.sowad.nourish.receivingaccount;

import com.sowad.nourish.receivingaccount.repository.JdbcReceivingAccountQueryRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class ReceivingAccountQueryServiceImpl implements ReceivingAccountQueryService {
  private final JdbcReceivingAccountQueryRepository repository;

  public ReceivingAccountQueryServiceImpl(JdbcReceivingAccountQueryRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> retrieve(String id) {
    return repository.retrieve(id);
  }

  public List<Map<String, Object>> list(
      String q, String kind, Boolean active, Integer limit, int offset) {
    return repository.list(q, kind, active, limit, offset);
  }
}
