package com.sowad.nourish.invoice;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.inventory.*;
import com.sowad.nourish.inventory.repository.StockRepository;
import com.sowad.nourish.invoice.command.CreateInvoice;
import com.sowad.nourish.payment.PaymentPosting;
import com.sowad.nourish.payment.command.CollectPayment;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class InvoiceServiceImpl implements InvoiceService {
  private final StockRepository stocks;
  private final SalePricing pricing;
  private final StockLedger ledger;
  private final OperationRequests requests;
  private final InvoiceQueries queries;
  private final SalesSupport json;
  private final PaymentPosting payments;
  private final CurrentUserProvider users;
  private final ActivityLogService audit;

  public InvoiceServiceImpl(
      StockRepository stocks,
      SalePricing pricing,
      StockLedger ledger,
      OperationRequests requests,
      InvoiceQueries queries,
      SalesSupport json,
      PaymentPosting payments,
      CurrentUserProvider users,
      ActivityLogService audit) {
    this.stocks = stocks;
    this.pricing = pricing;
    this.ledger = ledger;
    this.requests = requests;
    this.queries = queries;
    this.json = json;
    this.payments = payments;
    this.users = users;
    this.audit = audit;
  }

  public Map<String, Object> create(String key, CreateInvoice c) {
    String hash = requests.hash(c), prior = requests.begin(key, "invoice", hash);
    if (prior != null) return queries.retrieve(prior);
    SalesSupport.date(c.issuedAt(), "Invoice date");
    var zone = java.time.ZoneId.of("Asia/Dhaka");
    require(
        c.dueDate() != null
            && !c.dueDate()
                .atZone(zone)
                .toLocalDate()
                .isBefore(c.issuedAt().atZone(zone).toLocalDate())
            && c.dueDate().isBefore(java.time.Instant.parse("2100-01-01T00:00:00Z")),
        "Due date must be on/after invoice date and before 2100.");
    BigDecimal discount =
        c.discountAmount() == null
            ? BigDecimal.ZERO
            : SalesSupport.moneyInput(c.discountAmount(), "Discount", true);
    BigDecimal initial =
        c.initialPaid() == null
            ? BigDecimal.ZERO
            : SalesSupport.moneyInput(c.initialPaid(), "Initial payment", true);
    var customers =
        stocks
            .jdbc()
            .queryForList("SELECT * FROM nourish.customer WHERE id=? FOR UPDATE", c.customerId());
    if (customers.isEmpty()) throw MasterDataException.missing("Customer");
    var customer = customers.getFirst();
    if (!"active".equals(customer.get("status")) || Boolean.TRUE.equals(customer.get("archived")))
      throw MasterDataException.conflict(
          "Inactive/archived customer cannot receive a new invoice.");
    String notes = text(c.notes(), 500, "Notes"), lastNote = text(c.lastNote(), 500, "Last note");
    require(
        c.items() != null && !c.items().isEmpty() && c.items().size() <= 100,
        "Supply 1-100 distinct invoice products.");
    Map<String, CreateInvoice.Line> lines = new TreeMap<>();
    for (var l : c.items()) {
      require(
          l != null && l.productId() != null && !lines.containsKey(l.productId()),
          "Each product must appear once.");
      lines.put(l.productId(), l);
    }
    Map<String, SalePricing.Resolved> resolved = new HashMap<>();
    for (var e : lines.entrySet()) {
      var p = stocks.lockProduct(e.getKey());
      if (p.archived()) throw MasterDataException.conflict("Archived products cannot be sold.");
      resolved.put(p.id(), pricing.resolve(c.customerId(), p, stocks.packs(p.id()), e.getValue()));
    }
    BigDecimal
        subtotal =
            resolved.values().stream()
                .map(SalePricing.Resolved::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add),
        total = subtotal.subtract(discount);
    require(
        subtotal.compareTo(new BigDecimal("1000000000000000")) < 0 && total.signum() > 0,
        "Discount must leave a positive invoice total.");
    require(initial.compareTo(total) <= 0, "Initial payment cannot exceed invoice total.");
    if (initial.signum() == 0)
      require(
          text(c.receivingAccountId(), 64, "Account").isEmpty()
              && text(c.paymentProvider(), 10, "Provider").isEmpty()
              && text(c.senderNumber(), 20, "Sender").isEmpty()
              && text(c.transactionRef(), 80, "Reference").isEmpty(),
          "Payment metadata requires a positive initial payment.");
    String id = UUID.randomUUID().toString();
    Long seq =
        stocks.jdbc().queryForObject("SELECT nextval('nourish.invoice_number_seq')", Long.class);
    String number =
        "INV-"
            + c.issuedAt().atZone(zone).getYear()
            + "-"
            + String.format(Locale.ROOT, "%08d", seq);
    var actor = users.getCurrentUser();
    Map<String, Object> customerSnapshot = new LinkedHashMap<>();
    for (String f :
        List.of("id", "name_en", "name_bn", "address_en", "address_bn", "phone", "whatsapp_phone"))
      customerSnapshot.put(f, customer.get(f));
    var business =
        stocks
            .jdbc()
            .queryForMap(
                "SELECT business_name,owner_name,phone,email,address,invoice_footer,currency FROM"
                    + " nourish.business_settings WHERE id='business'");
    stocks
        .jdbc()
        .update(
            "INSERT INTO"
                + " nourish.invoice(id,invoice_number,customer_id,issued_at,due_date,subtotal,discount_amount,total_amount,notes,last_note,customer_snapshot,business_snapshot,actor)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)",
            id,
            number,
            c.customerId(),
            Timestamp.from(c.issuedAt()),
            Timestamp.from(c.dueDate()),
            subtotal,
            discount,
            total,
            notes,
            lastNote,
            json.encode(customerSnapshot),
            json.encode(business),
            actor.id());
    int position = 0;
    for (var l : c.items()) {
      var item = resolved.get(l.productId());
      String itemId = UUID.randomUUID().toString();
      var category =
          stocks
              .jdbc()
              .queryForMap(
                  "SELECT c.id,c.name FROM nourish.category c JOIN nourish.product p ON"
                      + " p.category_id=c.id WHERE p.id=?",
                  l.productId());
      stocks
          .jdbc()
          .update(
              "INSERT INTO"
                  + " nourish.invoice_item(id,invoice_id,line_number,product_id,product_name,sku,category_id,category_name,unit_code,quantity,unit_price,line_total,rate_snapshot)"
                  + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",
              itemId,
              id,
              ++position,
              l.productId(),
              item.product().name(),
              item.product().sku(),
              category.get("id"),
              category.get("name"),
              item.product().unit(),
              item.quantity(),
              item.unitPrice(),
              item.total(),
              json.encode(item.rows().stream().map(SalePricing.Row::snapshot).toList()));
      List<StockLedger.PackChange> changes = new ArrayList<>();
      for (var row : item.rows()) {
        stocks
            .jdbc()
            .update(
                "INSERT INTO"
                    + " nourish.invoice_package_detail(id,invoice_item_id,product_id,packaging_id,size_base,package_count,selling_per_package,line_total,price_adjustment)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                itemId,
                l.productId(),
                row.pack() == null ? null : row.pack().id(),
                row.size(),
                row.count(),
                row.price(),
                row.total(),
                row.total().subtract(row.price().multiply(BigDecimal.valueOf(row.count())).setScale(2, java.math.RoundingMode.HALF_UP)));
        if (row.pack() != null) {
          long before = stocks.count(row.pack().id());
          changes.add(new StockLedger.PackChange(row.pack(), before, before - row.count()));
        }
      }
      ledger.post(
          item.product(),
          stocks.balance(l.productId()),
          item.quantity().negate(),
          changes,
          "sale",
          id,
          c.issuedAt(),
          number,
          actor.id());
    }
    if (initial.signum() > 0) {
      String payment =
          payments.post(
              new CollectPayment(
                  id,
                  initial,
                  c.issuedAt(),
                  c.paymentMethod(),
                  c.receivingAccountId(),
                  c.paymentProvider(),
                  c.senderNumber(),
                  c.transactionRef(),
                  "Initial invoice payment"),
              actor.id());
      audit.record(actor, "CREATE", "payment", payment, "Initial invoice payment");
    }
    audit.record(actor, "CREATE", "invoice", id, "Sales invoice posted");
    requests.finish(key, "invoice", hash, id);
    return queries.retrieve(id);
  }
}
