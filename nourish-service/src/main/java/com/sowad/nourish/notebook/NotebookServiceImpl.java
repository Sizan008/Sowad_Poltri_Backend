package com.sowad.nourish.notebook;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.notebook.command.SaveNote;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class NotebookServiceImpl implements NotebookService {
  private final NotebookRepository repository;
  private final CurrentUserProvider users;
  private final ActivityLogService audit;

  public NotebookServiceImpl(
      NotebookRepository repository, CurrentUserProvider users, ActivityLogService audit) {
    this.repository = repository;
    this.users = users;
    this.audit = audit;
  }

  private void validate(SaveNote c) {
    require(text(c.title(), 160, "Title").length() >= 2, "Title requires 2-160 characters.");
    require(!text(c.content(), 10000, "Content").isEmpty(), "Content requires 1-10000 characters.");
  }

  private void version(Map<String, Object> row, Long expected) {
    if (expected != null && expected.longValue() != ((Number) row.get("revision")).longValue())
      throw MasterDataException.conflict("Note changed; refresh before editing.");
  }

  public Map<String, Object> create(SaveNote c) {
    validate(c);
    require(c.expectedRevision() == null, "New note must not specify expectedRevision.");
    var actor = users.getCurrentUser();
    String id = UUID.randomUUID().toString();
    repository
        .jdbc()
        .update(
            "INSERT INTO nourish.notebook(id,title,content,author_id,author_name,updated_by) VALUES"
                + " (?,?,?,?,?,?)",
            id,
            c.title().trim(),
            c.content().trim(),
            actor.id(),
            actor.displayName(),
            actor.id());
    record(id, "CREATE");
    return repository.get(id, false, false);
  }

  public Map<String, Object> update(String id, SaveNote c) {
    validate(c);
    var row = repository.get(id, true, false);
    version(row, c.expectedRevision());
    repository
        .jdbc()
        .update(
            "UPDATE nourish.notebook SET"
                + " title=?,content=?,updated_by=?,updated_at=now(),revision=revision+1 WHERE id=?",
            c.title().trim(),
            c.content().trim(),
            users.getCurrentUser().id(),
            id);
    record(id, "UPDATE");
    return repository.get(id, false, false);
  }

  public Map<String, Object> delete(String id, Long expected) {
    var row = repository.get(id, true, true);
    version(row, expected);
    if (!Boolean.TRUE.equals(row.get("deleted"))) {
      repository
          .jdbc()
          .update(
              "UPDATE nourish.notebook SET"
                  + " deleted=true,updated_by=?,updated_at=now(),revision=revision+1 WHERE id=?",
              users.getCurrentUser().id(),
              id);
      record(id, "DELETE");
    }
    return Map.of("deleted", true, "id", id);
  }

  private void record(String id, String action) {
    audit.record(
        users.getCurrentUser(),
        action,
        "notebook",
        id,
        "Notebook " + action.toLowerCase(Locale.ROOT));
  }
}
