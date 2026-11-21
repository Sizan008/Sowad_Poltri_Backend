package com.sowad.nourish.activitylog;

import com.sowad.nourish.common.security.CurrentUser;

/** Server-side events only. Never pass credentials or request payloads as details. */
public interface ActivityLogService {
    void record(CurrentUser actor, String action, String entity, String entityId, String details);
}
