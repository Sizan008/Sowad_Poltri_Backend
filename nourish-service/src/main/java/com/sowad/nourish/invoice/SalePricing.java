package com.sowad.nourish.invoice;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.inventory.repository.StockRepository.*;
import com.sowad.nourish.invoice.command.CreateInvoice.Line;
import java.math.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SalePricing {
  public record Selection(Pack pack, BigDecimal size, long count) {}

  public record Row(
      Pack pack,
      BigDecimal size,
      long count,
      BigDecimal price,
      BigDecimal total,
      Map<String, Object> snapshot) {}

  public record Resolved(
      Product product,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal total,
      List<Row> rows) {}

  private final JdbcTemplate jdbc;

  public SalePricing(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Resolved resolve(String customer, Product p, List<Pack> definitions, Line line) {
    require(
        line.pricingBasis() == null || Set.of("unit", "package").contains(line.pricingBasis()),
        "Invalid pricingBasis.");
    var bags =
        line.packages() == null
            ? List.<com.sowad.nourish.purchase.command.CreatePurchase.Bag>of()
            : line.packages();
    var meds =
        line.medicinePacks() == null
            ? List.<com.sowad.nourish.purchase.command.CreatePurchase.MedicinePack>of()
            : line.medicinePacks();
    require(bags.size() <= 50 && meds.size() <= 50, "Maximum 50 package sizes.");
    List<Selection> selected = new ArrayList<>();
    if (p.kind().equals("feed")) {
      require(
          !bags.isEmpty() && meds.isEmpty() && line.measurement() == null,
          "Feed sale requires bag sizes/counts only.");
      for (var b : bags) {
        require(b != null, "Bag required.");
        decimal(b.bagSizeKg(), "Bag size", 3, false);
        selected.add(select(definitions, b.bagSizeKg(), b.bagCount()));
      }
    } else if (p.kind().equals("medicine")) {
      require(!meds.isEmpty() && bags.isEmpty(), "Medicine sale requires pack sizes/counts.");
      for (var m : meds) {
        require(m != null && m.unitCode() != null, "Medicine pack required.");
        decimal(m.amount(), "Pack size", 3, false);
        selected.add(
            select(
                definitions, m.amount().multiply(factor(p.unit(), m.unitCode())), m.packCount()));
      }
    } else {
      require(
          bags.isEmpty() && meds.isEmpty() && line.measurement() == null,
          "Piece products cannot have package measurements.");
      decimal(line.quantity(), "Piece quantity", 0, false);
      selected.add(new Selection(null, BigDecimal.ONE, line.quantity().longValueExact()));
    }
    require(
        selected.stream().map(s -> s.size().stripTrailingZeros()).distinct().count()
            == selected.size(),
        "Duplicate package sizes.");
    BigDecimal quantity =
        selected.stream()
            .map(s -> s.size().multiply(BigDecimal.valueOf(s.count())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    decimal(quantity, "Quantity", p.unit().equals("piece") ? 0 : 3, false);
    if (line.quantity() != null)
      require(quantity.compareTo(line.quantity()) == 0, "Quantity differs from package total.");
    if (line.measurement() != null) {
      var m = line.measurement();
      decimal(m.amount(), "Measurement", 3, false);
      require(
          m.unitCode() != null
              && m.amount().multiply(factor(p.unit(), m.unitCode())).compareTo(quantity) == 0,
          "Measurement differs from package quantity.");
    }
    boolean special = "customer".equals(line.rateMode());
    boolean standard = "standard".equals(line.rateMode());
    require(line.rateMode() == null || special || standard, "Invalid invoice rate mode.");
    BigDecimal manual = line.manualLineTotal();
    if (manual != null) {
      require(line.rateMode() != null && manual.signum() > 0 && manual.stripTrailingZeros().scale() <= 2
          && manual.compareTo(new BigDecimal("1000000000000000")) < 0, "Enter a positive total with at most two decimal places.");
    }
    var units =
        jdbc.queryForList(
            "SELECT price_per_unit FROM nourish.customer_product_rate WHERE customer_id=? AND"
                + " product_id=?",
            customer,
            p.id());
    BigDecimal unit = units.isEmpty() ? null : (BigDecimal) units.getFirst().get("price_per_unit");
    String unitSource = "customer-unit";
    if (special && (unit == null || unit.signum() <= 0))
      throw MasterDataException.conflict("No customer unit rate is configured for " + p.name() + ".");
    if (standard) unit = null;
    if (unit == null) {
      unit =
          jdbc.queryForObject(
              "SELECT (SELECT sale_price FROM nourish.product_price WHERE product_id=?)",
              BigDecimal.class,
              p.id());
      unitSource = "global-unit";
    }
    List<Row> rows = new ArrayList<>();
    for (var s : selected) {
      BigDecimal packageRate =
          s.pack() == null || special || standard
              ? null
              : jdbc.queryForObject(
                  "SELECT (SELECT price_per_package FROM nourish.customer_package_rate WHERE"
                      + " customer_id=? AND packaging_id=?)",
                  BigDecimal.class,
                  customer,
                  s.pack().id());
      BigDecimal purchasePrice = null;
      if (!special && packageRate == null && (standard || units.isEmpty()) && p.kind().equals("feed")) {
        purchasePrice = feedPurchasePrice(p.id(), s.size());
      }
      if (standard && p.kind().equals("medicine")) {
        purchasePrice = jdbc.queryForObject("""
            SELECT (SELECT (r->>'suggestedSalePerPackage')::numeric
              FROM nourish.purchase_item i JOIN nourish.purchase p ON p.id=i.purchase_id
              CROSS JOIN LATERAL jsonb_array_elements(i.pricing_snapshot->'packages') r
              WHERE i.product_id=? AND (r->>'sizeBase')::numeric=?
                AND r->>'suggestedSalePerPackage' IS NOT NULL
              ORDER BY p.created_at DESC, i.id DESC LIMIT 1)
            """, BigDecimal.class, p.id(), s.size());
      }
      if (packageRate == null && purchasePrice == null && unit == null)
        throw MasterDataException.conflict(
            "No customer or global selling rate configured for " + p.name() + ".");
      BigDecimal chosen = packageRate != null ? packageRate : purchasePrice;
      if (chosen == null) chosen = special ? unit.multiply(s.size()) : money(unit.multiply(s.size()));
      require(chosen.signum() > 0, "Selling price rounds to zero.");
      BigDecimal total = money(chosen.multiply(BigDecimal.valueOf(s.count())));
      Map<String, Object> snap = new LinkedHashMap<>();
      snap.put("source", packageRate != null ? "customer-package" : purchasePrice != null ? "purchase-global" : unitSource);
      snap.put("customerUnitRate", units.isEmpty() ? null : units.getFirst().get("price_per_unit"));
      snap.put("selectedUnitRate", packageRate == null && purchasePrice == null ? unit : null);
      snap.put("customerPackageRate", packageRate);
      snap.put("sellingPricePerPackage", chosen);
      snap.put("sizeBase", s.size());
      snap.put("count", s.count());
      snap.put("packagingId", s.pack() == null ? null : s.pack().id());
      snap.put("amount", s.pack() == null ? BigDecimal.ONE : s.pack().amount());
      snap.put("unitCode", s.pack() == null ? p.unit() : s.pack().unit());
      snap.put("packageType", s.pack() == null ? "piece" : s.pack().type());
      rows.add(new Row(s.pack(), s.size(), s.count(), chosen, total, snap));
    }
    BigDecimal total = rows.stream().map(Row::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (special || manual != null) {
      BigDecimal target = manual != null ? money(manual) : money(unit.multiply(quantity));
      require(target.signum() > 0, "Invoice line total rounds to zero.");
      // Cumulative rounding ensures allocations add to the exact invoice amount.
      BigDecimal weight = BigDecimal.ZERO;
      BigDecimal previous = BigDecimal.ZERO;
      BigDecimal denominator = special ? quantity : total;
      for (int i = 0; i < rows.size(); i++) {
        Row r = rows.get(i);
        weight = weight.add(special ? r.size().multiply(BigDecimal.valueOf(r.count())) : r.total());
        BigDecimal cumulative = target.multiply(weight).divide(denominator, 2, RoundingMode.HALF_UP);
        BigDecimal allocated = cumulative.subtract(previous);
        r.snapshot().put("rateMode", line.rateMode());
        r.snapshot().put("manualLineTotal", manual);
        r.snapshot().put("chargedPackageGroupTotal", allocated);
        rows.set(i, new Row(r.pack(), r.size(), r.count(), r.price(), allocated, r.snapshot()));
        previous = cumulative;
      }
      total = target;
    }
    require(
        total.compareTo(new BigDecimal("1000000000000000")) < 0, "Invoice line total too large.");
    BigDecimal effective = total.divide(quantity, 6, RoundingMode.HALF_UP);
    require(effective.signum() > 0, "Effective unit price rounds to zero.");
    if (line.unitPrice() != null && line.unitPrice().compareTo(effective) != 0)
      throw MasterDataException.conflict(
          "Selling rate changed or client rate differs. Refresh rates; omit unitPrice to use server"
              + " calculation.");
    return new Resolved(p, quantity, effective, total, rows);
  }

  /** Resolve the latest applicable selling instruction, including purchases recorded
   * before bag selling prices were exposed as invoice defaults. A cost-only purchase
   * does not replace the previous selling instruction. */
  private BigDecimal feedPurchasePrice(String productId, BigDecimal bagSize) {
    return jdbc.queryForObject("""
        WITH rates AS (
          SELECT i.id, p.created_at, m.stock_revision, (r->>'sizeBase')::numeric AS size,
                 (r->>'suggestedSalePerPackage')::numeric AS effective_bag_rate,
                 (r->>'suggestedSalePricePerPackageEntered')::numeric AS bag_rate,
                 (r->>'suggestedSaleRatePerUnit')::numeric AS kg_rate,
                 CASE WHEN (i.pricing_snapshot->>'setGlobalSellingRate')::boolean
                      THEN (i.pricing_snapshot->>'unitSellingRate')::numeric END AS shared_rate
          FROM nourish.purchase_item i
          JOIN nourish.purchase p ON p.id=i.purchase_id
          LEFT JOIN nourish.stock_movement m ON m.product_id=i.product_id
              AND m.related_id=i.purchase_id AND m.movement_type='purchase'
          CROSS JOIN LATERAL jsonb_array_elements(i.pricing_snapshot->'packages') r
          WHERE i.product_id=?
        ), candidates AS (
          SELECT id, created_at, stock_revision, coalesce(
            max(effective_bag_rate) FILTER (WHERE size=?),
            max(bag_rate) FILTER (WHERE size=?),
            round(max(kg_rate) FILTER (WHERE size=?) * ?, 2),
            round(max(shared_rate) * ?, 2),
            CASE WHEN min(kg_rate)=max(kg_rate) THEN round(max(kg_rate) * ?, 2) END
          ) AS price
          FROM rates GROUP BY id, created_at, stock_revision
        )
        SELECT (SELECT price FROM candidates WHERE price IS NOT NULL
                ORDER BY created_at DESC, stock_revision DESC NULLS LAST, id DESC LIMIT 1)
        """, BigDecimal.class, productId, bagSize, bagSize, bagSize, bagSize, bagSize, bagSize);
  }

  private Selection select(List<Pack> definitions, BigDecimal size, Long count) {
    require(
        count != null && count > 0 && count <= 1000000000L,
        "Positive integer package count required.");
    var pack =
        definitions.stream()
            .filter(p -> p.active() && p.size().compareTo(size) == 0)
            .findFirst()
            .orElseThrow(() -> new MasterDataException(400, "Choose active registered packaging."));
    return new Selection(pack, size, count);
  }
}
