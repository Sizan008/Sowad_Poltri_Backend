package com.sowad.nourish.activitylog.repository;

import com.sowad.nourish.activitylog.repository.entity.ActivityLogEntity;
import com.sowad.nourish.common.repository.CommandRepository;

public interface ActivityLogRepository extends CommandRepository<ActivityLogEntity, String> {}
