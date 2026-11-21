package com.sowad.nourish.catalog;

import com.sowad.nourish.catalog.command.SavePackaging;
import com.sowad.nourish.catalog.repository.entity.*;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PackagingRules {
  public void apply(PackagingEntity pack, ProductEntity product, SavePackaging c) {
    Set<String> units =
        switch (product.getUnit()) {
          case "kg" -> Set.of("kg");
          case "g" -> Set.of("g", "kg");
          case "ml" -> Set.of("ml", "l");
          default -> Set.of("piece");
        };
    if (!units.contains(c.unitCode()))
      throw new CatalogFailure(400, "Package unit does not match the product's base unit.");
    if (product.getUnit().equals("piece") && c.amount().compareTo(BigDecimal.ONE) != 0)
      throw new CatalogFailure(400, "Chicks and fish use one piece per definition.");
    BigDecimal factor =
        (c.unitCode().equals("l") || (product.getUnit().equals("g") && c.unitCode().equals("kg")))
            ? BigDecimal.valueOf(1000)
            : BigDecimal.ONE;
    pack.setAmount(c.amount());
    pack.setUnitCode(c.unitCode());
    pack.setSizeBase(c.amount().multiply(factor));
    pack.setProductId(product.getId());
    pack.setPackageType(
        switch (product.getKind()) {
          case "feed" -> "bag";
          case "medicine" -> product.getUnit().equals("ml") ? "bottle" : "packet";
          default -> "piece";
        });
    pack.setActive(true);
  }
}
