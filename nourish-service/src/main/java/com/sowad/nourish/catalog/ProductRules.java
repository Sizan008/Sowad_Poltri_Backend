package com.sowad.nourish.catalog;

import com.sowad.nourish.catalog.command.SaveProduct;
import com.sowad.nourish.catalog.repository.entity.ProductEntity;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProductRules {
  public static String clean(String s) {
    return s == null ? "" : s.trim();
  }

  public String unit(String kind, SaveProduct c) {
    if (clean(c.name()).length() < 2)
      throw new CatalogFailure(400, "Product name needs at least 2 characters.");
    return switch (kind) {
      case "feed" -> {
        require(
            !clean(c.feedGroup()).isEmpty() && !clean(c.feedType()).isEmpty(),
            "Feed group and type are required.");
        yield "kg";
      }
      case "medicine" -> {
        require(
            clean(c.manufacturer()).length() >= 2
                && Set.of("powder", "liquid").contains(clean(c.medicineForm())),
            "Medicine company and Powder/Liquid form are required.");
        yield "liquid".equals(c.medicineForm()) ? "ml" : "g";
      }
      case "chick" -> {
        require(
            Set.of("broiler", "sonali", "colorbird", "layer").contains(clean(c.subtype()))
                && Set.of("A", "B+").contains(clean(c.grade())),
            "Choose an allowed chick breed and grade.");
        yield "piece";
      }
      case "fish" -> {
        require(
            Set.of("tilapia", "sarputi").contains(clean(c.subtype())),
            "Choose Tilapia or Sarputi.");
        yield "piece";
      }
      default -> throw new CatalogFailure(400, "Choose an active category.");
    };
  }

  public void apply(ProductEntity p, SaveProduct c, String kind) {
    String unit = unit(kind, c);
    String sku = clean(c.sku()).toUpperCase(Locale.ROOT);
    if (p.getId() != null) {
      if (p.getArchived())
        throw CatalogFailure.conflict("Archived products cannot be edited or reactivated.");
      if (!p.getCategoryId().equals(c.categoryId()) || !p.getUnit().equals(unit))
        throw CatalogFailure.conflict("Registered category and base unit cannot change.");
      if (p.getFeedCatalogId() != null
          && (!p.getName().equals(clean(c.name()))
              || !p.getSku().equals(sku)
              || !p.getFeedGroup().equals(clean(c.feedGroup()))
              || !p.getFeedType().equals(clean(c.feedType()))))
        throw CatalogFailure.conflict(
            "Official feed identity is fixed. Register a new SKU for a new product.");
    }
    p.setName(clean(c.name()));
    p.setSku(sku);
    p.setKind(kind);
    p.setCategoryId(c.categoryId());
    p.setUnit(unit);
    p.setSubtype(clean(c.subtype()));
    p.setGrade(kind.equals("chick") ? clean(c.grade()) : null);
    p.setManufacturer(kind.equals("medicine") ? clean(c.manufacturer()) : "");
    p.setMedicineForm(kind.equals("medicine") ? clean(c.medicineForm()) : null);
    p.setFeedGroup(kind.equals("feed") ? clean(c.feedGroup()) : "");
    p.setFeedType(kind.equals("feed") ? clean(c.feedType()) : "");
    if (c.lowStockThreshold() != null) p.setLowStockThreshold(c.lowStockThreshold());
    else if (p.getLowStockThreshold() == null) p.setLowStockThreshold(java.math.BigDecimal.ZERO);
  }

  private void require(boolean ok, String message) {
    if (!ok) throw new CatalogFailure(400, message);
  }
}
