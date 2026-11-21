package com.sowad.nourish.settings;

import com.sowad.nourish.settings.query.BusinessSettings;
import com.sowad.nourish.settings.repository.JdbcBusinessSettingsQueryRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessSettingsQueryServiceImpl implements BusinessSettingsQueryService {
    private final JdbcBusinessSettingsQueryRepository repository;

    public BusinessSettingsQueryServiceImpl(JdbcBusinessSettingsQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('admin','operator','viewer')")
    public BusinessSettings retrieveSettings() {
        return repository.retrieveSettings();
    }
}
