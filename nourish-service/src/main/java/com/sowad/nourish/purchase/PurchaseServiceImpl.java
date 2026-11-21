package com.sowad.nourish.purchase;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.inventory.*;
import com.sowad.nourish.inventory.repository.StockRepository;
import com.sowad.nourish.purchase.command.CreatePurchase;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class PurchaseServiceImpl implements PurchaseService {
  private final StockRepository stocks;
  private final PurchasePricing pricing;
  private final StockLedger ledger;
  private final OperationRequests requests;
  private final PurchaseQueries queries;
  private final ObjectMapper json;
  private final CurrentUserProvider users;
  private final ActivityLogService audit;

  public PurchaseServiceImpl(
      StockRepository stocks,
      PurchasePricing pricing,
      StockLedger ledger,
      OperationRequests requests,
      PurchaseQueries queries,
      ObjectMapper json,
      CurrentUserProvider users,
      ActivityLogService audit) {
    this.stocks = stocks;
    this.pricing = pricing;
    this.ledger = ledger;
    this.requests = requests;
    this.queries = queries;
    this.json = json;
    this.users = users;
    this.audit = audit;
  }

  public Map<String, Object> create(String key, CreatePurchase c) {
    String hash = requests.hash(c), prior = requests.begin(key, "purchase", hash);
    if (prior != null) return queries.retrieve(prior);
    require(
        c.purchasedAt() != null
            && !c.purchasedAt().isBefore(java.time.Instant.parse("2000-01-01T00:00:00Z"))
            && c.purchasedAt().isBefore(java.time.Instant.now().plusSeconds(86400)),
        "Purchase date must be from 2000 through today (one day timezone allowance).");
    String reference = text(c.reference(), 80, "Reference"), notes = text(c.notes(), 500, "Notes");
    var suppliers =
        stocks
            .jdbc()
            .queryForList(
                "SELECT name_en,status,archived FROM nourish.supplier WHERE id=? FOR UPDATE",
                c.supplierId());
    if (suppliers.isEmpty()) throw MasterDataException.missing("Supplier");
    var supplier = suppliers.getFirst();
    if (!"active".equals(supplier.get("status")) || Boolean.TRUE.equals(supplier.get("archived")))
      throw MasterDataException.conflict("Supplier is inactive/archived.");
    require(
        c.items() != null && !c.items().isEmpty() && c.items().size() <= 100,
        "Supply 1-100 purchase items.");
    Map<String, CreatePurchase.Line> lines = new TreeMap<>();
    for (var l : c.items()) {
      require(
          l != null && l.productId() != null && !lines.containsKey(l.productId()),
          "Each product must appear exactly once.");
      lines.put(l.productId(), l);
    }
    Map<String, PurchasePricing.Resolved> resolved = new HashMap<>();
    for (var entry : lines.entrySet()) {
      var p = stocks.lockProduct(entry.getKey());
      if (p.archived()) throw MasterDataException.conflict("Archived product cannot be purchased.");
      resolved.put(p.id(), pricing.resolve(p, stocks.packs(p.id()), entry.getValue()));
    }
    BigDecimal total =
        resolved.values().stream()
            .map(PurchasePricing.Resolved::total)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    require(total.compareTo(new BigDecimal("1000000000000000")) < 0, "Purchase total too large.");
    String id = UUID.randomUUID().toString();
    var actor = users.getCurrentUser();
    stocks
        .jdbc()
        .update(
            "INSERT INTO"
                + " nourish.purchase(id,purchase_number,supplier_id,supplier_name,purchased_at,reference,notes,total_amount)"
                + " VALUES (?,?,?,?,?,?,?,?)",
            id,
            "PUR-" + id.toUpperCase(Locale.ROOT),
            c.supplierId(),
            supplier.get("name_en"),
            Timestamp.from(c.purchasedAt()),
            reference,
            notes,
            total);
    int position = 0;
    for (var line : c.items()) {
      var item = resolved.get(line.productId());
      String itemId = UUID.randomUUID().toString();
      stocks
          .jdbc()
          .update(
              "INSERT INTO"
                  + " nourish.purchase_item(id,purchase_id,line_number,product_id,product_name,sku,unit_code,quantity,unit_price,line_total,pricing_snapshot)"
                  + " VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)",
              itemId,
              id,
              ++position,
              item.product().id(),
              item.product().name(),
              item.product().sku(),
              item.product().unit(),
              item.quantity(),
              item.unitPrice(),
              item.total(),
              encode(item.snapshot()));
      List<StockLedger.PackChange> changes = new ArrayList<>();
      for (var row : item.rows()) {
        stocks
            .jdbc()
            .update(
                "INSERT INTO"
                    + " nourish.purchase_package_detail(id,purchase_item_id,product_id,packaging_id,size_base,package_count,purchase_per_package,line_total)"
                    + " VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                itemId,
                item.product().id(),
                row.pack() == null ? null : row.pack().id(),
                row.size(),
                row.count(),
                row.snapshot().get("purchasePerPackage"),
                row.total());
        if (row.pack() != null) {
          long before = stocks.count(row.pack().id());
          changes.add(
              new StockLedger.PackChange(row.pack(), before, Math.addExact(before, row.count())));
        }
      }
      var balance = stocks.balance(item.product().id());
      ledger.post(
          item.product(),
          balance,
          item.quantity(),
          changes,
          "purchase",
          id,
          c.purchasedAt(),
          reference,
          actor.id());
      stocks.prices(item.product().id(), item.unitPrice(), item.globalSale());
    }
    audit.record(actor, "CREATE", "purchase", id, "Purchase posted with stock movements");
    requests.finish(key, "purchase", hash, id);
    return queries.retrieve(id);
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
