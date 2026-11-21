package com.sowad.nourish.receivingaccount;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.receivingaccount.command.SaveReceivingAccount;
import com.sowad.nourish.receivingaccount.repository.ReceivingAccountRepository;
import com.sowad.nourish.receivingaccount.repository.entity.ReceivingAccountEntity;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasRole('admin')")
public class ReceivingAccountServiceImpl implements ReceivingAccountService {
  private final ReceivingAccountRepository repository;
  private final ReceivingAccountQueryService queries;
  private final ReceivingAccountRules rules;
  private final ActivityLogService audit;
  private final CurrentUserProvider users;

  public ReceivingAccountServiceImpl(
      ReceivingAccountRepository repository,
      ReceivingAccountQueryService queries,
      ReceivingAccountRules rules,
      ActivityLogService audit,
      CurrentUserProvider users) {
    this.repository = repository;
    this.queries = queries;
    this.rules = rules;
    this.audit = audit;
    this.users = users;
  }

  public Map<String, Object> create(SaveReceivingAccount command) {
    var entity = new ReceivingAccountEntity();
    entity.setId(UUID.randomUUID().toString());
    rules.apply(entity, command);
    entity.setCreatedAt(Instant.now());
    repository.saveAndFlush(entity);
    record("CREATE", entity.getId());
    return queries.retrieve(entity.getId());
  }

  public Map<String, Object> update(String id, SaveReceivingAccount command) {
    var entity =
        repository.lock(id).orElseThrow(() -> MasterDataException.missing("Receiving account"));
    rules.apply(entity, command);
    repository.flush();
    record("UPDATE", id);
    return queries.retrieve(id);
  }

  public Map<String, Object> deactivate(String id) {
    var entity =
        repository.lock(id).orElseThrow(() -> MasterDataException.missing("Receiving account"));
    if (entity.getActive()) {
      entity.setActive(false);
      entity.setUpdatedAt(Instant.now());
      repository.flush();
      record("DEACTIVATE", id);
    }
    return queries.retrieve(id);
  }

  private void record(String action, String id) {
    audit.record(
        users.getCurrentUser(),
        action,
        "receiving-account",
        id,
        "Receiving account " + action.toLowerCase(Locale.ROOT));
  }
}
