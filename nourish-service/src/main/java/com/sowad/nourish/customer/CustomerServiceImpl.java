package com.sowad.nourish.customer;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.contact.ContactRules;
import com.sowad.nourish.contact.command.SaveContact;
import com.sowad.nourish.customer.repository.CustomerRepository;
import com.sowad.nourish.customer.repository.entity.CustomerEntity;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class CustomerServiceImpl implements CustomerService {
  private final CustomerRepository repository;
  private final CustomerQueryService queries;
  private final ContactRules rules;
  private final ActivityLogService audit;
  private final CurrentUserProvider users;

  public CustomerServiceImpl(
      CustomerRepository repository,
      CustomerQueryService queries,
      ContactRules rules,
      ActivityLogService audit,
      CurrentUserProvider users) {
    this.repository = repository;
    this.queries = queries;
    this.rules = rules;
    this.audit = audit;
    this.users = users;
  }

  public Map<String, Object> create(SaveContact command) {
    var entity = new CustomerEntity();
    rules.apply(entity, command);
    entity.setId(UUID.randomUUID().toString());
    entity.setCreatedAt(Instant.now());
    repository.saveAndFlush(entity);
    record("CREATE", entity.getId());
    return queries.retrieve(entity.getId());
  }

  public Map<String, Object> update(String id, SaveContact command) {
    var entity = repository.lock(id).orElseThrow(() -> MasterDataException.missing("Customer"));
    rules.apply(entity, command);
    repository.flush();
    record("UPDATE", id);
    return queries.retrieve(id);
  }

  public void archive(String id) {
    var entity = repository.lock(id).orElseThrow(() -> MasterDataException.missing("Customer"));
    if (entity.getArchived()) return;
    rules.archive(entity);
    repository.flush();
    record("ARCHIVE", id);
  }

  private void record(String action, String id) {
    audit.record(
        users.getCurrentUser(),
        action,
        "customer",
        id,
        "Customer " + action.toLowerCase(Locale.ROOT));
  }
}
