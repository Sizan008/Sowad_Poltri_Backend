package com.sowad.nourish.catalog.repository;

import com.sowad.nourish.catalog.repository.entity.PackagingEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PackagingRepository extends JpaRepository<PackagingEntity, String> {
  Optional<PackagingEntity> findByProductIdAndSizeBase(String productId, java.math.BigDecimal sizeBase);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from PackagingEntity e where e.id=:id")
  Optional<PackagingEntity> lock(@Param("id") String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from PackagingEntity e where e.id=:id and e.productId=:productId")
  Optional<PackagingEntity> lockForProduct(
      @Param("id") String id, @Param("productId") String productId);
}
