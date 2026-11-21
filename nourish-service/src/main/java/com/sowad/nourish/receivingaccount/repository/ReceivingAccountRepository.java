package com.sowad.nourish.receivingaccount.repository;

import com.sowad.nourish.receivingaccount.repository.entity.ReceivingAccountEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReceivingAccountRepository extends JpaRepository<ReceivingAccountEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from ReceivingAccountEntity e where e.id=:id")
  Optional<ReceivingAccountEntity> lock(@Param("id") String id);
}
