package com.sowad.nourish.inventory;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.inventory.repository.StockRepository;
import com.sowad.nourish.inventory.repository.StockRepository.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class StockLedger {
  public record PackChange(Pack pack, long before, long after) {}

  private final StockRepository stocks;

  public StockLedger(StockRepository stocks) {
    this.stocks = stocks;
  }

  // Caller holds product lock; all changes join the caller's transaction.
  public String post(
      Product product,
      Balance before,
      BigDecimal delta,
      List<PackChange> packs,
      String type,
      String related,
      Instant occurred,
      String reason,
      String actor) {
    BigDecimal after = before.quantity().add(delta);
    if (after.signum() < 0) throw MasterDataException.conflict("Insufficient stock.");
    StockNumbers.decimal(after, "Resulting stock", product.unit().equals("piece") ? 0 : 3, true);
    for (var p : packs) {
      if (p.after() < 0 || p.after() > 1000000000L)
        throw MasterDataException.conflict("Invalid resulting package stock.");
      stocks.packageCount(p.pack(), p.after());
    }
    stocks.balance(product.id(), after);
    String id = UUID.randomUUID().toString();
    stocks
        .jdbc()
        .update(
            "INSERT INTO"
                + " nourish.stock_movement(id,product_id,movement_type,related_id,quantity_change,stock_before,stock_after,stock_revision,occurred_at,reason,actor)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            id,
            product.id(),
            type,
            related,
            delta,
            before.quantity(),
            after,
            before.revision() + 1,
            Timestamp.from(occurred),
            reason,
            actor);
    for (var p : packs)
      stocks
          .jdbc()
          .update(
              "INSERT INTO"
                  + " nourish.stock_movement_package(movement_id,packaging_id,product_id,size_base,count_before,count_after)"
                  + " VALUES (?,?,?,?,?,?)",
              id,
              p.pack().id(),
              product.id(),
              p.pack().size(),
              p.before(),
              p.after());
    return id;
  }
}
