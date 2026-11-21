package com.sowad.nourish.messagecenter;

import com.sowad.nourish.customer.CustomerQueryService;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class MessageCenterController {
  private final CustomerQueryService customers;

  public MessageCenterController(CustomerQueryService customers) {
    this.customers = customers;
  }

  @GetMapping("/messageCenter")
  public Object capabilities() {
    return Map.of(
        "scope",
        "recipient-selection-only",
        "sendingEnabled",
        false,
        "modes",
        List.of("all", "selected", "individual"));
  }

  @GetMapping("/messageCenter/recipients")
  public Object recipients(
      @RequestParam(required = false) @Size(max = 120) String q,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return customers.list(q, "active", false, limit, offset).stream()
        .map(
            r ->
                Map.of(
                    "id",
                    r.get("id"),
                    "name",
                    r.get("name"),
                    "nameBn",
                    r.get("nameBn"),
                    "phone",
                    r.get("phone")))
        .toList();
  }
}
