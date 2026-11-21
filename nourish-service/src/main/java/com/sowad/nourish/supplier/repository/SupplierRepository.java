package com.sowad.nourish.supplier.repository;

import com.sowad.nourish.supplier.repository.entity.SupplierEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<SupplierEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from SupplierEntity e where e.id=:id")
  Optional<SupplierEntity> lock(@Param("id") String id);
}
