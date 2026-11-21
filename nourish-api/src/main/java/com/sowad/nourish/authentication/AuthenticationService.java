package com.sowad.nourish.authentication;

import com.sowad.nourish.authentication.command.Login;
import com.sowad.nourish.authentication.query.LoginResult;

public interface AuthenticationService {
    LoginResult process(Login command);

    void logout();
}
