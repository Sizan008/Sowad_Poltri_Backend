package com.sowad.nourish.catalog.repository;

import com.sowad.nourish.catalog.repository.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from ProductEntity e where e.id=:id")
  Optional<ProductEntity> lock(@Param("id") String id);

  boolean existsBySkuIgnoreCase(String sku);
}
