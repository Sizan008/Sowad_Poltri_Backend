package com.sowad.nourish.settings.query;

import java.math.BigDecimal;

public record BusinessSettings(
        String id,
        String businessName,
        String ownerName,
        String phone,
        String email,
        String address,
        String invoiceFooter,
        BigDecimal lowStockDefault,
        String currency,
        String uiLanguage) {}
