package com.sowad.nourish.purchase;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.sowad.nourish.inventory.repository.StockRepository.*;
import com.sowad.nourish.purchase.command.CreatePurchase.*;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PurchasePricing {
  public record Row(
      Pack pack,
      String key,
      BigDecimal size,
      long count,
      Map<String, Object> snapshot,
      BigDecimal total) {}

  public record Resolved(
      Product product,
      BigDecimal quantity,
      BigDecimal total,
      BigDecimal unitPrice,
      BigDecimal globalSale,
      List<Row> rows,
      Map<String, Object> snapshot) {}

  private record Selected(Pack pack, String key, BigDecimal size, long count) {}

  public Resolved resolve(Product product, List<Pack> definitions, Line line) {
    require(line != null, "Purchase item required.");
    require(
        line.pricingBasis() == null || Set.of("unit", "package").contains(line.pricingBasis()),
        "Invalid pricingBasis.");
    List<Selected> selected = new ArrayList<>();
    var bags = line.packages() == null ? List.<Bag>of() : line.packages();
    var medicines = line.medicinePacks() == null ? List.<MedicinePack>of() : line.medicinePacks();
    require(bags.size() <= 50 && medicines.size() <= 50, "Maximum 50 package sizes per product.");
    if (product.kind().equals("feed")) {
      require(
          !bags.isEmpty() && medicines.isEmpty() && line.measurement() == null,
          "Feed purchases require bag sizes/counts only.");
      for (var b : bags) {
        require(b != null, "Invalid bag.");
        decimal(b.bagSizeKg(), "Bag size", 3, false);
        selected.add(select(definitions, "bag:" + key(b.bagSizeKg()), b.bagSizeKg(), b.bagCount()));
      }
    } else if (product.kind().equals("medicine")) {
      require(
          !medicines.isEmpty() && bags.isEmpty(),
          "Medicine purchases require registered pack sizes/counts.");
      for (var p : medicines) {
        require(p != null && p.unitCode() != null, "Invalid medicine pack.");
        decimal(p.amount(), "Pack size", 3, false);
        BigDecimal size = p.amount().multiply(factor(product.unit(), p.unitCode()));
        selected.add(select(definitions, "medicine:" + key(size), size, p.packCount()));
      }
    } else {
      require(
          bags.isEmpty() && medicines.isEmpty() && line.measurement() == null,
          "Piece products cannot use package measurements.");
      decimal(line.quantity(), "Piece quantity", 0, false);
      selected.add(new Selected(null, "piece", BigDecimal.ONE, line.quantity().longValueExact()));
    }
    require(
        selected.stream().map(Selected::key).distinct().count() == selected.size(),
        "Duplicate package size.");
    BigDecimal quantity =
        selected.stream()
            .map(p -> p.size().multiply(BigDecimal.valueOf(p.count())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    decimal(quantity, "Quantity", product.unit().equals("piece") ? 0 : 3, false);
    if (line.quantity() != null)
      require(
          line.quantity().compareTo(quantity) == 0,
          "Quantity does not equal package sizes multiplied by counts.");
    if (line.measurement() != null) {
      var m = line.measurement();
      decimal(m.amount(), "Measurement", 3, false);
      require(
          m.unitCode() != null
              && m.amount().multiply(factor(product.unit(), m.unitCode())).compareTo(quantity) == 0,
          "Medicine measurement differs from package total.");
    }
    Pricing pricing = line.purchasePricing();
    if (pricing == null) {
      require(
          !"package".equals(line.pricingBasis()),
          "Explicit purchasePricing is required for package pricing.");
      pricing = new Pricing(rate(line.unitPrice(), "Unit purchase rate"), null, false, List.of());
    }
    BigDecimal sharedCost = rate(pricing.unitPurchaseRate(), "Unit purchase rate"),
        sharedSale = rate(pricing.unitSellingRate(), "Unit selling rate");
    boolean global = Boolean.TRUE.equals(pricing.setGlobalSellingRate());
    require(
        !global || sharedSale != null,
        "setGlobalSellingRate requires unitSellingRate in the product base unit.");
    var prices = pricing.packagePrices() == null ? List.<Price>of() : pricing.packagePrices();
    require(prices.size() <= 50, "Too many package prices.");
    Map<String, Price> supplied = new HashMap<>();
    for (var price : prices) {
      require(
          price != null && price.key() != null && !supplied.containsKey(price.key()),
          "Missing/duplicate package price key.");
      supplied.put(price.key(), price);
    }
    List<Row> rows = new ArrayList<>();
    for (var s : selected) {
      Price price = supplied.remove(s.key());
      if (price == null) price = new Price(s.key(), null, null, null, null, null, null);
      BigDecimal directCost = rate(price.purchasePricePerPackage(), "Package purchase price"),
          directSale = rate(price.suggestedSalePricePerPackage(), "Package sale suggestion");
      BigDecimal unitCost = rate(price.purchaseRatePerUnit(), "Package unit purchase rate"),
          unitSale = rate(price.suggestedSaleRatePerUnit(), "Package unit sale suggestion");
      BigDecimal cf = factor(product.unit(), price.purchaseRateUnit()),
          sf = factor(product.unit(), price.suggestedSaleRateUnit());
      BigDecimal cost = resolve(directCost, unitCost, cf, sharedCost, s.size()),
          sale = resolve(directSale, unitSale, sf, sharedSale, s.size());
      require(
          cost != null && cost.signum() > 0,
          "Every package/piece requires a purchase price that rounds to at least 0.01.");
      require(sale == null || sale.signum() > 0, "Suggested selling price rounds to zero.");
      BigDecimal total = money(cost.multiply(BigDecimal.valueOf(s.count())));
      Map<String, Object> snap = new LinkedHashMap<>();
      snap.put("key", s.key());
      snap.put("packagingId", s.pack() == null ? null : s.pack().id());
      snap.put("label", s.key());
      snap.put("packagingAmount", s.pack() == null ? BigDecimal.ONE : s.pack().amount());
      snap.put("packagingUnitCode", s.pack() == null ? "piece" : s.pack().unit());
      snap.put("packageType", s.pack() == null ? "piece" : s.pack().type());
      snap.put("unitCode", product.unit());
      snap.put("sizeBase", s.size());
      snap.put("count", s.count());
      snap.put("purchasePerPackage", cost);
      snap.put("suggestedSalePerPackage", sale);
      snap.put("purchaseLineTotal", total);
      snap.put("source", directCost != null ? "package" : "unit");
      snap.put("purchasePricePerPackageEntered", directCost);
      snap.put("suggestedSalePricePerPackageEntered", directSale);
      snap.put("purchaseRatePerUnit", unitCost);
      snap.put(
          "purchaseRateUnit",
          unitCost == null
              ? null
              : Objects.requireNonNullElse(price.purchaseRateUnit(), product.unit()));
      snap.put("suggestedSaleRatePerUnit", unitSale);
      snap.put(
          "suggestedSaleRateUnit",
          unitSale == null
              ? null
              : Objects.requireNonNullElse(price.suggestedSaleRateUnit(), product.unit()));
      rows.add(new Row(s.pack(), s.key(), s.size(), s.count(), snap, total));
    }
    require(supplied.isEmpty(), "Package price key does not match a selected size.");
    BigDecimal total = rows.stream().map(Row::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    require(
        total.compareTo(new BigDecimal("1000000000000000")) < 0, "Purchase line total too large.");
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("unitPurchaseRate", sharedCost);
    snapshot.put("unitSellingRate", sharedSale);
    snapshot.put("setGlobalSellingRate", global);
    snapshot.put("packages", rows.stream().map(Row::snapshot).toList());
    return new Resolved(
        product,
        quantity,
        total,
        total.divide(quantity, 6, RoundingMode.HALF_UP),
        global ? sharedSale : null,
        rows,
        snapshot);
  }

  private Selected select(List<Pack> defs, String key, BigDecimal size, Long count) {
    require(
        count != null && count > 0 && count <= 1000000000L,
        "Package count must be a positive integer, maximum 1 billion.");
    Pack p =
        defs.stream()
            .filter(v -> v.active() && v.size().compareTo(size) == 0)
            .findFirst()
            .orElseThrow(
                () ->
                    new com.sowad.nourish.common.masterdata.MasterDataException(
                        400, "Register/activate this packaging size before purchasing."));
    return new Selected(p, key, size, count);
  }

  private BigDecimal resolve(
      BigDecimal direct, BigDecimal unit, BigDecimal factor, BigDecimal shared, BigDecimal size) {
    if (direct != null) return money(direct);
    if (unit != null) return money(unit.multiply(size).divide(factor));
    return shared == null ? null : money(shared.multiply(size));
  }
}
