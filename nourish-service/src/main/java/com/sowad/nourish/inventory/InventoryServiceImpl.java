package com.sowad.nourish.inventory;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.inventory.command.ChangeStock;
import com.sowad.nourish.inventory.repository.StockRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasRole('admin')")
public class InventoryServiceImpl implements InventoryService {
  private final StockRepository stocks;
  private final StockLedger ledger;
  private final OperationRequests requests;
  private final InventoryQueries queries;
  private final CurrentUserProvider users;
  private final ActivityLogService audit;

  public InventoryServiceImpl(
      StockRepository stocks,
      StockLedger ledger,
      OperationRequests requests,
      InventoryQueries queries,
      CurrentUserProvider users,
      ActivityLogService audit) {
    this.stocks = stocks;
    this.ledger = ledger;
    this.requests = requests;
    this.queries = queries;
    this.users = users;
    this.audit = audit;
  }

  public Map<String, Object> change(String key, ChangeStock c) {
    String hash = requests.hash(c), prior = requests.begin(key, "adjustment", hash);
    if (prior != null) return queries.adjustment(prior);
    require(
        c.mode() != null && Set.of("delta", "recount").contains(c.mode()),
        "mode must be delta or recount.");
    boolean recount = c.mode().equals("recount");
    String reason = text(c.reason(), 500, "Reason");
    require(reason.length() >= 3, "Adjustment/recount reason required.");
    var p = stocks.lockProduct(c.productId());
    if (p.archived()) throw MasterDataException.conflict("Archived product cannot be adjusted.");
    var before = stocks.balance(p.id());
    require(
        !recount || c.expectedRevision() != null,
        "Recount requires expectedRevision from current inventory.");
    if (c.expectedRevision() != null && c.expectedRevision() != before.revision())
      throw MasterDataException.conflict("Stock changed. Refresh stock and recount again.");
    List<StockLedger.PackChange> changes = new ArrayList<>();
    BigDecimal delta = BigDecimal.ZERO;
    var counts = c.packages() == null ? List.<ChangeStock.PackageCount>of() : c.packages();
    if (p.kind().equals("feed") || p.kind().equals("medicine")) {
      require(
          c.quantity() == null && !counts.isEmpty() && counts.size() <= 50,
          "Packaged products require 1-50 package counts; omit quantity.");
      var definitions = stocks.packs(p.id());
      Set<String> seen = new HashSet<>();
      for (var count : counts) {
        require(
            count != null
                && count.packagingId() != null
                && seen.add(count.packagingId())
                && count.count() != null
                && count.count() >= -1000000000L
                && count.count() <= 1000000000L,
            "Invalid/duplicate package count.");
        var pack =
            definitions.stream()
                .filter(d -> d.id().equals(count.packagingId()))
                .findFirst()
                .orElseThrow(() -> MasterDataException.missing("Product packaging"));
        long old = stocks.count(pack.id());
        long after = recount ? count.count() : old + count.count();
        require(!recount || count.count() >= 0, "Recount cannot be negative.");
        if (!pack.active() && after > old)
          throw MasterDataException.conflict("Inactive packaging cannot receive stock.");
        changes.add(new StockLedger.PackChange(pack, old, after));
        delta = delta.add(pack.size().multiply(BigDecimal.valueOf(after - old)));
      }
      // Recount is a full physical count, not a partial overwrite.
      if (recount)
        for (var pack : definitions)
          require(
              stocks.count(pack.id()) == 0 || seen.contains(pack.id()),
              "Recount must include every package size with existing stock (use zero if absent).");
    } else {
      require(counts.isEmpty() && c.quantity() != null, "Piece stock requires quantity only.");
      decimal(c.quantity().abs(), "Piece count", 0, true);
      if (recount) require(c.quantity().signum() >= 0, "Recount cannot be negative.");
      delta = recount ? c.quantity().subtract(before.quantity()) : c.quantity();
    }
    require(
        recount || delta.signum() != 0 || changes.stream().anyMatch(v -> v.before() != v.after()),
        "Adjustment must change stock.");
    String id = UUID.randomUUID().toString();
    var actor = users.getCurrentUser();
    stocks
        .jdbc()
        .update(
            "INSERT INTO nourish.stock_adjustment(id,product_id,mode,reason,expected_revision)"
                + " VALUES (?,?,?,?,?)",
            id,
            p.id(),
            c.mode(),
            reason,
            c.expectedRevision());
    ledger.post(
        p,
        before,
        delta,
        changes,
        recount ? "recount" : "adjustment",
        id,
        Instant.now(),
        reason,
        actor.id());
    audit.record(actor, "CREATE", "stock_adjustment", id, reason);
    requests.finish(key, "adjustment", hash, id);
    return queries.adjustment(id);
  }
}
