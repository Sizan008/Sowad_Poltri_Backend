package com.sowad.nourish.common.repository;

import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** Extend per feature; transaction boundaries belong to its command service. */
@NoRepositoryBean
public interface CommandRepository<T, ID> extends Repository<T, ID> {
    <S extends T> S save(S entity);
    Optional<T> findById(ID id);
}
