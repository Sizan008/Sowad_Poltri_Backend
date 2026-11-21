package com.sowad.nourish.migration;

import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class MigrationController {
  private final MigrationService service;

  public MigrationController(MigrationService service) {
    this.service = service;
  }

  @PostMapping("/migration/{batch}/{collection}/{ordinal}/promote")
  public Object promote(
      @PathVariable @Pattern(regexp = "[0-9a-f]{64}") String batch,
      @PathVariable @Size(max = 80) String collection,
      @PathVariable @Min(0) int ordinal,
      @RequestBody String body)
      throws Exception {
    return service.promote(batch, collection, ordinal, body);
  }

  @PostMapping("/migration/{batch}/{collection}/{ordinal}/link")
  public Object link(
      @PathVariable @Pattern(regexp = "[0-9a-f]{64}") String batch,
      @PathVariable @Size(max = 80) String collection,
      @PathVariable @Min(0) int ordinal,
      @RequestBody String body)
      throws Exception {
    return service.link(batch, collection, ordinal, body);
  }

  @GetMapping("/verification/reconciliation")
  public Object reconciliation() {
    return service.reconciliation();
  }
}
