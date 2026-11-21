package com.sowad.nourish.user;

import com.sowad.nourish.common.security.CurrentUser;
import com.sowad.nourish.user.repository.JdbcUserQueryRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserQueryServiceImpl implements UserQueryService {
    private final JdbcUserQueryRepository repository;

    public UserQueryServiceImpl(JdbcUserQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('admin')")
    public List<CurrentUser> retrieveUsers(int limit, int offset) {
        return repository.retrieveUsers(limit, offset);
    }
}
