package com.sowad.nourish.customer.repository;

import com.sowad.nourish.customer.repository.entity.CustomerEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CustomerEntity e where e.id=:id")
  Optional<CustomerEntity> lock(@Param("id") String id);
}
