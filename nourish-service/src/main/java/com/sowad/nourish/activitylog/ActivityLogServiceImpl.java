package com.sowad.nourish.activitylog;

import com.sowad.nourish.activitylog.repository.ActivityLogRepository;
import com.sowad.nourish.activitylog.repository.entity.ActivityLogEntity;
import com.sowad.nourish.common.security.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Service
public class ActivityLogServiceImpl implements ActivityLogService {
    private final ActivityLogRepository repository;

    public ActivityLogServiceImpl(ActivityLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            CurrentUser actor, String action, String entity, String entityId, String details) {
        var log = new ActivityLogEntity();
        log.setId(UUID.randomUUID().toString());
        log.setActorId(actor == null ? null : actor.id());
        log.setActorName(actor == null ? "anonymous" : actor.displayName());
        log.setRole(actor == null ? "anonymous" : actor.role());
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setOccurredAt(Instant.now());
        repository.save(log);
    }
}
