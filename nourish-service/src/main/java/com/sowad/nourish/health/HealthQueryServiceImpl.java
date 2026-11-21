package com.sowad.nourish.health;

import com.sowad.nourish.health.query.HealthDetails;
import com.sowad.nourish.health.repository.HealthQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthQueryServiceImpl implements HealthQueryService {
  private final HealthQueryRepository repository;

  public HealthQueryServiceImpl(HealthQueryRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public HealthDetails retrieveHealth() {
    var state = repository.retrieveDatabaseState();
    boolean ready =
        "nourish_poltri_shop".equals(state.database())
            && "nourish".equals(state.schema())
            && state.version() == 8;
    return new HealthDetails(
        ready ? "ok" : "down", 8, "postgresql", state.database(), state.schema(), state.version());
  }
}
