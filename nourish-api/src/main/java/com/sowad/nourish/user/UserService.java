package com.sowad.nourish.user;

import com.sowad.nourish.common.security.CurrentUser;
import com.sowad.nourish.user.command.CreateUser;

public interface UserService {
    CurrentUser process(CreateUser command);
}
