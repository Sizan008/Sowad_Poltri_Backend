package com.sowad.nourish.invoice;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.inventory.repository.StockRepository;
import com.sowad.nourish.invoice.command.CreateInvoice.Line;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class SaleQuoteService {
  public record Quote(String customerId, List<Line> items) {}

  private final StockRepository stocks;
  private final SalePricing pricing;

  public SaleQuoteService(StockRepository stocks, SalePricing pricing) {
    this.stocks = stocks;
    this.pricing = pricing;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Object quote(Quote command) {
    MasterDataException.require(
        command.items() != null
            && !command.items().isEmpty()
            && command.items().size() <= 100,
        "1..100 items required.");
    if (command.customerId() != null) {
      Boolean valid =
          stocks
              .jdbc()
              .queryForObject(
                  "SELECT EXISTS(SELECT 1 FROM nourish.customer WHERE id=? AND NOT archived AND"
                      + " status='active')",
                  Boolean.class,
                  command.customerId());
      MasterDataException.require(Boolean.TRUE.equals(valid), "Choose an active customer.");
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    BigDecimal total = BigDecimal.ZERO;
    for (var line : command.items()) {
      MasterDataException.require(
          line != null && line.productId() != null && seen.add(line.productId()),
          "Product required and must not be repeated.");
      var products =
          stocks
              .jdbc()
              .query(
                  "SELECT id,name,sku,kind,unit_code,archived FROM nourish.product WHERE id=?",
                  (r, n) ->
                      new StockRepository.Product(
                          r.getString(1),
                          r.getString(2),
                          r.getString(3),
                          r.getString(4),
                          r.getString(5),
                          r.getBoolean(6)),
                  line.productId());
      if (products.isEmpty()) throw MasterDataException.missing("Product");
      var p = products.getFirst();
      MasterDataException.require(!p.archived(), "Archived products cannot be sold.");
      var withoutClientRate =
          new Line(
              line.productId(),
              line.quantity(),
              null,
              line.pricingBasis(),
              line.packages(),
              line.medicinePacks(),
              line.measurement(), line.rateMode(), line.manualLineTotal());
      var resolved =
          pricing.resolve(command.customerId(), p, stocks.packs(p.id()), withoutClientRate);
      var quoted = new LinkedHashMap<String, Object>(Map.of(
              "productId",
              p.id(),
              "quantity",
              resolved.quantity(),
              "unitPrice",
              resolved.unitPrice(),
              "bagPrices", p.kind().equals("feed") ? resolved.rows().stream().map(row -> Map.of(
                  "sizeKg", row.size(), "count", row.count(), "pricePerBag", row.price(),
                  "total", row.total())).toList() : List.of(),
              "lineTotal",
              resolved.total()));
      if ("customer".equals(line.rateMode()))
        quoted.put("customerUnitRate", resolved.rows().getFirst().snapshot().get("customerUnitRate"));
      rows.add(quoted);
      total = total.add(resolved.total());
    }
    return Map.of("items", rows, "subtotal", total, "stockReserved", false);
  }
}
