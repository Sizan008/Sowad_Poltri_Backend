package com.sowad.nourish.supplier;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.contact.command.SaveContact;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class SupplierController {
  private final SupplierService commands;
  private final SupplierQueryService queries;
  private final StrictCommandDecoder decoder;

  public SupplierController(
      SupplierService commands, SupplierQueryService queries, StrictCommandDecoder decoder) {
    this.commands = commands;
    this.queries = queries;
    this.decoder = decoder;
  }

  @GetMapping("/suppliers")
  public Object list(
      @RequestParam(required = false) @Size(max = 120) String q,
      @RequestParam(required = false) @Pattern(regexp = "active|inactive") String status,
      @RequestParam(required = false) Boolean archived,
      @RequestParam(required = false) @Min(1) @Max(200) Integer limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(q, status, archived, limit, offset);
  }

  @GetMapping("/suppliers/{id}")
  public Object retrieve(@PathVariable String id) {
    return queries.retrieve(id);
  }

  @GetMapping("/suppliers/{id}/details")
  public Object details(@PathVariable String id) {
    return queries.details(id);
  }

  @PostMapping("/suppliers")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(@RequestBody String body) {
    return commands.create(decoder.decode(body, SaveContact.class));
  }

  @PutMapping("/suppliers/{id}")
  public Object update(@PathVariable String id, @RequestBody String body) {
    return commands.update(id, decoder.decode(body, SaveContact.class));
  }

  @DeleteMapping("/suppliers/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void archive(@PathVariable String id) {
    commands.archive(id);
  }
}
