package com.sowad.nourish.customerrate;

import static com.sowad.nourish.common.masterdata.MasterDataException.require;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.catalog.repository.*;
import com.sowad.nourish.catalog.repository.entity.*;
import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.customer.CustomerQueryService;
import com.sowad.nourish.customer.repository.CustomerRepository;
import com.sowad.nourish.customerrate.command.SaveCustomerRates;
import com.sowad.nourish.customerrate.repository.*;
import com.sowad.nourish.customerrate.repository.entity.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class CustomerRateServiceImpl implements CustomerRateService {
  private final CustomerRepository customers;
  private final ProductRepository products;
  private final PackagingRepository packaging;
  private final CustomerProductRateRepository units;
  private final CustomerPackageRateRepository packages;
  private final CustomerQueryService queries;
  private final ActivityLogService audit;
  private final CurrentUserProvider users;

  public CustomerRateServiceImpl(
      CustomerRepository customers,
      ProductRepository products,
      PackagingRepository packaging,
      CustomerProductRateRepository units,
      CustomerPackageRateRepository packages,
      CustomerQueryService queries,
      ActivityLogService audit,
      CurrentUserProvider users) {
    this.customers = customers;
    this.products = products;
    this.packaging = packaging;
    this.units = units;
    this.packages = packages;
    this.queries = queries;
    this.audit = audit;
    this.users = users;
  }

  public Map<String, Object> replace(String customerId, SaveCustomerRates command) {
    var customer =
        customers.lock(customerId).orElseThrow(() -> MasterDataException.missing("Customer"));
    if (customer.getArchived() || !customer.getStatus().equals("active"))
      throw MasterDataException.conflict(
          "Rates can only be changed for active, non-archived customers.");
    // Lock product parents in deterministic order before package children, matching catalog
    // writers.
    Map<String, ProductEntity> lockedProducts = new HashMap<>();
    for (String id :
        command.rates().stream()
            .map(SaveCustomerRates.Rate::productId)
            .distinct()
            .sorted()
            .toList()) {
      var product = products.lock(id).orElseThrow(() -> MasterDataException.missing("Product"));
      require(!product.getArchived(), "Choose an active product.");
      lockedProducts.put(id, product);
    }
    List<CustomerProductRateEntity> unitRows = new ArrayList<>();
    List<CustomerPackageRateEntity> packageRows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (var rate : command.rates()) {
      ProductEntity product = lockedProducts.get(rate.productId());
      require(
          rate.unit() == null || rate.unit().equals(product.getUnit()),
          "Rate unit must match the product base unit.");
      if (rate.packagingId() != null) {
        require(!rate.packagingId().isBlank(), "packagingId cannot be blank.");
        require(
            rate.pricePerPackage() != null
                && rate.pricePerUnit() == null
                && rate.pricePerKg() == null,
            "Package rates require only pricePerPackage.");
        require(seen.add("package:" + rate.packagingId()), "Duplicate package rate.");
        var pack =
            packaging
                .lockForProduct(rate.packagingId(), rate.productId())
                .orElseThrow(
                    () ->
                        new MasterDataException(
                            400, "Packaging definition must belong to this product."));
        require(
            pack.getProductId().equals(rate.productId()),
            "Packaging does not belong to this product.");
        require(pack.getActive(), "Choose an active packaging definition.");
        var row = new CustomerPackageRateEntity();
        row.setId(UUID.randomUUID().toString());
        row.setCustomerId(customerId);
        row.setProductId(rate.productId());
        row.setPackagingId(rate.packagingId());
        row.setPricePerPackage(rate.pricePerPackage());
        packageRows.add(row);
      } else {
        require(rate.pricePerPackage() == null, "A package price requires packagingId.");
        require(seen.add("unit:" + rate.productId()), "Duplicate product unit rate.");
        require(
            rate.pricePerKg() == null || product.getKind().equals("feed"),
            "Legacy pricePerKg is supported for feed only.");
        if (rate.pricePerUnit() != null && rate.pricePerKg() != null)
          require(
              rate.pricePerUnit().compareTo(rate.pricePerKg()) == 0,
              "pricePerUnit and pricePerKg disagree.");
        BigDecimal value = rate.pricePerUnit() != null ? rate.pricePerUnit() : rate.pricePerKg();
        require(value != null, "A positive pricePerUnit is required.");
        var row = new CustomerProductRateEntity();
        row.setId(UUID.randomUUID().toString());
        row.setCustomerId(customerId);
        row.setProductId(rate.productId());
        row.setPricePerUnit(value);
        unitRows.add(row);
      }
    }
    // Validation is complete before replacing any existing configuration. Audit failures roll back
    // all changes.
    units.deleteForCustomer(customerId);
    packages.deleteForCustomer(customerId);
    units.saveAllAndFlush(unitRows);
    packages.saveAllAndFlush(packageRows);
    audit.record(
        users.getCurrentUser(),
        "RATES_REPLACE",
        "customer-rate",
        customerId,
        "Customer rate configuration replaced");
    return queries.retrieve(customerId);
  }
}
