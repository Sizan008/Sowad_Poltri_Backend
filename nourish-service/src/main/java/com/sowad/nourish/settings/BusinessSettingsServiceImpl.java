package com.sowad.nourish.settings;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.settings.command.UpdateBusinessSettings;
import com.sowad.nourish.settings.repository.BusinessSettingsRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BusinessSettingsServiceImpl implements BusinessSettingsService {
    private final BusinessSettingsRepository repository;
    private final CurrentUserProvider users;
    private final ActivityLogService audit;

    public BusinessSettingsServiceImpl(
            BusinessSettingsRepository repository,
            CurrentUserProvider users,
            ActivityLogService audit) {
        this.repository = repository;
        this.users = users;
        this.audit = audit;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('admin')")
    public void process(UpdateBusinessSettings command) {
        var settings =
                repository
                        .findById("business")
                        .orElseThrow(() -> new IllegalStateException("Missing settings seed"));
        settings.setBusinessName(command.businessName().trim());
        settings.setOwnerName(command.ownerName());
        settings.setPhone(command.phone());
        settings.setEmail(command.email());
        settings.setAddress(command.address());
        settings.setInvoiceFooter(command.invoiceFooter());
        settings.setLowStockDefault(command.lowStockDefault());
        settings.setUiLanguage(command.uiLanguage());
        settings.setUpdatedAt(Instant.now());
        var actor = users.getCurrentUser();
        settings.setUpdatedBy(actor.id());
        audit.record(actor, "UPDATE", "settings", "business", "Business settings updated");
    }
}
