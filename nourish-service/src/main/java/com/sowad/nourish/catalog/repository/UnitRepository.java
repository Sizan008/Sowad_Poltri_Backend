package com.sowad.nourish.catalog.repository;

import com.sowad.nourish.catalog.repository.entity.UnitEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<UnitEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from UnitEntity e where e.id=:id")
  Optional<UnitEntity> lock(@Param("id") String id);

  boolean existsByCode(String code);
}
