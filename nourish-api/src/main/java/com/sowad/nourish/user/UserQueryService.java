package com.sowad.nourish.user;

import com.sowad.nourish.common.security.CurrentUser;

import java.util.List;

public interface UserQueryService {
    List<CurrentUser> retrieveUsers(int limit, int offset);
}
