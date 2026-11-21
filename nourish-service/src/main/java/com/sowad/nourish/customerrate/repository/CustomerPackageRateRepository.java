package com.sowad.nourish.customerrate.repository;

import com.sowad.nourish.customerrate.repository.entity.CustomerPackageRateEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CustomerPackageRateRepository
    extends JpaRepository<CustomerPackageRateEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CustomerPackageRateEntity e where e.id=:id")
  Optional<CustomerPackageRateEntity> lock(@Param("id") String id);

  @Modifying
  @Query("delete from CustomerPackageRateEntity e where e.customerId=:id")
  void deleteForCustomer(@Param("id") String id);
}
