package com.sowad.nourish.activitylog.query;

import java.time.LocalDate;

public record ActivityLogFilter(
    String q,
    String text,
    String actorId,
    String role,
    String action,
    String entity,
    String entityId,
    LocalDate from,
    LocalDate to) {}
