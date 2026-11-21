package com.sowad.nourish.user.repository;

import com.sowad.nourish.common.repository.CommandRepository;
import com.sowad.nourish.user.repository.entity.UserEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends CommandRepository<UserEntity, String> {
    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.username = :username")
    Optional<UserEntity> findForLogin(@Param("username") String username);
}
