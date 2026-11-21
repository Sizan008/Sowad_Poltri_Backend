package com.sowad.nourish.notebook;

import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class NotebookQueries {
  private final NotebookRepository repository;

  public NotebookQueries(NotebookRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> page(int page, int size, String q, String order) {
    return repository.page(page, size, q, order);
  }

  public Object get(String id) {
    return repository.get(id, false, false);
  }
}
