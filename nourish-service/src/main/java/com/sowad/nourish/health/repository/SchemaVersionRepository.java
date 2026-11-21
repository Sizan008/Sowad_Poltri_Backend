package com.sowad.nourish.health.repository;

import com.sowad.nourish.health.repository.entity.SchemaVersionEntity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read-only JPA mapping. Versions are written only by manual SQL. */
public interface SchemaVersionRepository
        extends Repository<SchemaVersionEntity, Integer> {
    Optional<SchemaVersionEntity> findById(Integer version);
}
