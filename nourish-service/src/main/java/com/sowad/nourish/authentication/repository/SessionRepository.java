package com.sowad.nourish.authentication.repository;

import com.sowad.nourish.authentication.repository.entity.SessionEntity;
import com.sowad.nourish.common.repository.CommandRepository;

public interface SessionRepository extends CommandRepository<SessionEntity, String> {}
