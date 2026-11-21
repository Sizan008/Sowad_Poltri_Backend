package com.sowad.nourish.bootstrap;

import com.sowad.nourish.user.UserServiceImpl;
import com.sowad.nourish.user.command.CreateUser;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nourish.bootstrap.enabled", havingValue = "true")
@ConditionalOnNotWebApplication
public class InitialAdminRunner implements ApplicationRunner {
    private final UserServiceImpl users;
    private final ConfigurableApplicationContext context;

    public InitialAdminRunner(UserServiceImpl users, ConfigurableApplicationContext context) {
        this.users = users;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            String username = required("NOURISH_ADMIN_USERNAME");
            String displayName = required("NOURISH_ADMIN_DISPLAY_NAME");
            String password = required("NOURISH_ADMIN_PASSWORD");
            users.bootstrap(new CreateUser(username, password, displayName, "admin"));
            System.out.println(
                    "Initial admin created. Start the server normally; bootstrap cannot be"
                        + " repeated.");
        } finally {
            context.close();
        }
    }

    private String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing local setup environment: " + key);
        return value;
    }
}
