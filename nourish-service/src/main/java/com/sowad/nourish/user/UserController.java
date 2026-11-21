package com.sowad.nourish.user;

import com.sowad.nourish.common.security.CurrentUser;
import com.sowad.nourish.user.command.CreateUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@Validated
public class UserController {
    private final UserService service;
    private final UserQueryService queries;

    public UserController(UserService service, UserQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CurrentUser create(@Valid @RequestBody CreateUser command) {
        return service.process(command);
    }

    @GetMapping
    public List<CurrentUser> list(
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return queries.retrieveUsers(limit, offset);
    }
}
