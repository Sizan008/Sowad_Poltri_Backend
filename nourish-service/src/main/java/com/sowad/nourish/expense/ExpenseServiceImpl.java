package com.sowad.nourish.expense;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.expense.command.SaveExpense;
import com.sowad.nourish.inventory.OperationRequests;
import com.sowad.nourish.invoice.SalesSupport;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class ExpenseServiceImpl implements ExpenseService {
  private final ExpenseRepository repository;
  private final CurrentUserProvider users;
  private final ActivityLogService audit;
  private final OperationRequests requests;

  public ExpenseServiceImpl(
      ExpenseRepository repository,
      CurrentUserProvider users,
      ActivityLogService audit,
      OperationRequests requests) {
    this.repository = repository;
    this.users = users;
    this.audit = audit;
    this.requests = requests;
  }

  private void validate(SaveExpense c) {
    require(
        text(c.title(), 120, "Title").length() >= 3, "Expense title must have 3-120 characters.");
    require(
        c.category() != null
            && Set.of(
                    "rent", "utilities", "salary", "transport", "maintenance", "supplies", "other")
                .contains(c.category()),
        "Invalid expense category.");
    require(
        c.method() != null && Set.of("cash", "bank", "mobile-banking").contains(c.method()),
        "Invalid expense method.");
    SalesSupport.moneyInput(c.amount(), "Expense amount", false);
    SalesSupport.date(c.spentAt(), "Expense date");
    text(c.reference(), 80, "Reference");
    text(c.note(), 500, "Note");
  }

  private void version(Map<String, Object> row, Long expected) {
    if (expected != null && expected.longValue() != ((Number) row.get("revision")).longValue())
      throw MasterDataException.conflict("Expense changed; refresh before editing.");
  }

  public Map<String, Object> create(String key, SaveExpense c) {
    String hash = requests.hash(c), prior = requests.begin(key, "expense", hash);
    if (prior != null) return repository.get(prior, false, true);
    validate(c);
    require(c.expectedRevision() == null, "New expense must not specify expectedRevision.");
    String id = UUID.randomUUID().toString(), actor = users.getCurrentUser().id();
    repository
        .jdbc()
        .update(
            "INSERT INTO"
                + " nourish.expense(id,title,category,method,amount,spent_at,reference,note,created_by,updated_by)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?)",
            id,
            c.title().trim(),
            c.category(),
            c.method(),
            c.amount(),
            Timestamp.from(c.spentAt()),
            text(c.reference(), 80, "Reference"),
            text(c.note(), 500, "Note"),
            actor,
            actor);
    record(id, "CREATE");
    requests.finish(key, "expense", hash, id);
    return repository.get(id, false, false);
  }

  public Map<String, Object> update(String id, SaveExpense c) {
    validate(c);
    var row = repository.get(id, true, false);
    version(row, c.expectedRevision());
    repository
        .jdbc()
        .update(
            "UPDATE nourish.expense SET"
                + " title=?,category=?,method=?,amount=?,spent_at=?,reference=?,note=?,updated_by=?,updated_at=now(),revision=revision+1"
                + " WHERE id=?",
            c.title().trim(),
            c.category(),
            c.method(),
            c.amount(),
            Timestamp.from(c.spentAt()),
            text(c.reference(), 80, "Reference"),
            text(c.note(), 500, "Note"),
            users.getCurrentUser().id(),
            id);
    record(id, "UPDATE");
    return repository.get(id, false, false);
  }

  public Map<String, Object> delete(String id, Long expected) {
    var row = repository.get(id, true, true);
    version(row, expected);
    if (Boolean.TRUE.equals(row.get("deleted"))) return row;
    repository
        .jdbc()
        .update(
            "UPDATE nourish.expense SET"
                + " deleted=true,revision=revision+1,updated_at=now(),updated_by=? WHERE id=?",
            users.getCurrentUser().id(),
            id);
    record(id, "DELETE");
    return repository.get(id, false, true);
  }

  private void record(String id, String action) {
    var actor = users.getCurrentUser();
    repository.history(id, action, actor.id());
    audit.record(actor, action, "expense", id, "Expense " + action.toLowerCase(Locale.ROOT));
  }
}
