package com.sowad.nourish.settings;

import com.sowad.nourish.settings.command.UpdateBusinessSettings;

public interface BusinessSettingsService {
    void process(UpdateBusinessSettings command);
}
