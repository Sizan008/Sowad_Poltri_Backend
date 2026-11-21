package com.sowad.nourish.catalog;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.catalog.command.*;
import com.sowad.nourish.catalog.repository.*;
import com.sowad.nourish.catalog.repository.entity.*;
import com.sowad.nourish.common.security.CurrentUserProvider;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class CatalogServiceImpl implements CatalogService {
  private final ProductRepository products;
  private final com.sowad.nourish.inventory.StockPolicy stockPolicy;
  private final UnitRepository units;
  private final PackagingRepository packs;
  private final JdbcCatalogQueryRepository reads;
  private final CatalogQueryService queries;
  private final ProductRules rules;
  private final PackagingRules packagingRules;
  private final ActivityLogService audit;
  private final CurrentUserProvider users;

  public CatalogServiceImpl(
      ProductRepository products,
      com.sowad.nourish.inventory.StockPolicy stockPolicy,
      UnitRepository units,
      PackagingRepository packs,
      JdbcCatalogQueryRepository reads,
      CatalogQueryService queries,
      ProductRules rules,
      PackagingRules packagingRules,
      ActivityLogService audit,
      CurrentUserProvider users) {
    this.products = products;
    this.stockPolicy = stockPolicy;
    this.units = units;
    this.packs = packs;
    this.reads = reads;
    this.queries = queries;
    this.rules = rules;
    this.packagingRules = packagingRules;
    this.audit = audit;
    this.users = users;
  }

  private void log(String action, String entity, String id) {
    audit.record(
        users.getCurrentUser(),
        action,
        entity,
        id,
        "Catalog " + entity + " " + action.toLowerCase(Locale.ROOT));
  }

  public Map<String, Object> createProduct(SaveProduct c) {
    ProductEntity p = new ProductEntity();
    rules.apply(p, c, reads.kind(c.categoryId()));
    if (products.existsBySkuIgnoreCase(p.getSku()))
      throw CatalogFailure.conflict("SKU already registered, including archived products.");
    p.setId(UUID.randomUUID().toString());
    products.saveAndFlush(p);
    log("CREATE", "product", p.getId());
    return queries.product(p.getId());
  }

  public Map<String, Object> editProduct(String id, SaveProduct c) {
    ProductEntity p = products.lock(id).orElseThrow(CatalogFailure::missing);
    rules.apply(p, c, reads.kind(c.categoryId()));
    products.flush();
    log("UPDATE", "product", id);
    return queries.product(id);
  }

  public void archiveProduct(String id) {
    ProductEntity p = products.lock(id).orElseThrow(CatalogFailure::missing);
    if (p.getFeedCatalogId() != null)
      throw CatalogFailure.conflict("Official feed catalog products cannot be archived.");
    if (p.getArchived()) return;
    stockPolicy.requireEmptyProduct(id);
    p.setArchived(true);
    products.flush();
    log("ARCHIVE", "product", id);
  }

  private void applyUnit(UnitEntity u, SaveUnit c) {
    if (c.id() != null && u.getId() != null && !c.id().equals(u.getId()))
      throw new CatalogFailure(400, "Unit ID cannot change.");
    if (u.getId() != null
        && (!u.getCode().equals(c.code()) || u.getAllowDecimal() != c.allowDecimal()))
      throw CatalogFailure.conflict(
          "Unit code and decimal behavior are immutable. Create a separate unit.");
    u.setCode(c.code());
    u.setName(c.name().trim());
    u.setSymbol(c.symbol().trim());
    u.setAllowDecimal(c.allowDecimal());
  }

  public Map<String, Object> createUnit(SaveUnit c) {
    UnitEntity u = new UnitEntity();
    applyUnit(u, c);
    u.setId(UUID.randomUUID().toString());
    units.saveAndFlush(u);
    log("CREATE", "unit", u.getId());
    return queries.unit(u.getId());
  }

  public Map<String, Object> editUnit(String id, SaveUnit c) {
    UnitEntity u = units.lock(id).orElseThrow(CatalogFailure::missing);
    applyUnit(u, c);
    units.flush();
    log("UPDATE", "unit", id);
    return queries.unit(id);
  }

  public void deleteUnit(String id) {
    UnitEntity u = units.lock(id).orElseThrow(CatalogFailure::missing);
    if (u.getSystemUnit()) throw CatalogFailure.conflict("System units cannot be deleted.");
    units.delete(u);
    units.flush();
    log("DELETE", "unit", id);
  }

  public Map<String, Object> createPackaging(String productId, SavePackaging c) {
    ProductEntity p = products.lock(productId).orElseThrow(CatalogFailure::missing);
    if (p.getArchived())
      throw CatalogFailure.conflict("Archived products cannot receive packaging definitions.");
    PackagingEntity pack = new PackagingEntity();
    packagingRules.apply(pack, p, c);
    var existing = packs.findByProductIdAndSizeBase(productId, pack.getSizeBase());
    String action = "CREATE";
    if (existing.isPresent()) {
      var previous = existing.get();
      if (previous.getActive()) throw CatalogFailure.conflict("Packaging size is already active.");
      previous.setActive(true);
      pack = previous;
      action = "ACTIVATE";
    } else {
      pack.setId(UUID.randomUUID().toString());
    }
    packs.saveAndFlush(pack);
    String packagingId = pack.getId();
    log(action, "packaging", packagingId);
    return queries.packaging(productId).stream()
        .filter(r -> packagingId.equals(r.get("id")))
        .findFirst()
        .orElseThrow();
  }

  public void deactivatePackaging(String productId, String id) {
    products.lock(productId).orElseThrow(CatalogFailure::missing);
    PackagingEntity p = packs.lock(id).orElseThrow(CatalogFailure::missing);
    if (!p.getProductId().equals(productId)) throw CatalogFailure.missing();
    if (!p.getActive()) return;
    stockPolicy.requireEmptyPackaging(id);
    p.setActive(false);
    packs.flush();
    log("DEACTIVATE", "packaging", id);
  }
}
