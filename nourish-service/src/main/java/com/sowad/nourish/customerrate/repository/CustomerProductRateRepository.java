package com.sowad.nourish.customerrate.repository;

import com.sowad.nourish.customerrate.repository.entity.CustomerProductRateEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CustomerProductRateRepository
    extends JpaRepository<CustomerProductRateEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CustomerProductRateEntity e where e.id=:id")
  Optional<CustomerProductRateEntity> lock(@Param("id") String id);

  @Modifying
  @Query("delete from CustomerProductRateEntity e where e.customerId=:id")
  void deleteForCustomer(@Param("id") String id);
}
