package com.sowad.nourish.settings;

import com.sowad.nourish.settings.command.UpdateBusinessSettings;
import com.sowad.nourish.settings.query.BusinessSettings;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
public class BusinessSettingsController {
    private final BusinessSettingsService service;
    private final BusinessSettingsQueryService queries;

    public BusinessSettingsController(
            BusinessSettingsService service, BusinessSettingsQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping("/businessSettings/business")
    public BusinessSettings retrieveSettings() {
        return queries.retrieveSettings();
    }

    @PutMapping("/transactions/settings")
    public BusinessSettings update(@Valid @RequestBody UpdateBusinessSettings command) {
        service.process(command);
        return queries.retrieveSettings();
    }
}
