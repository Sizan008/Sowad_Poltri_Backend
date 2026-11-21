package com.sowad.nourish.customer;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.contact.command.SaveContact;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class CustomerController {
  private final CustomerService commands;
  private final CustomerQueryService queries;
  private final StrictCommandDecoder decoder;
  private final com.sowad.nourish.customerrate.CustomerRateService rates;

  public CustomerController(
      CustomerService commands,
      CustomerQueryService queries,
      StrictCommandDecoder decoder,
      com.sowad.nourish.customerrate.CustomerRateService rates) {
    this.commands = commands;
    this.queries = queries;
    this.decoder = decoder;
    this.rates = rates;
  }

  @GetMapping("/customers")
  public Object list(
      @RequestParam(required = false) @Size(max = 120) String q,
      @RequestParam(required = false) @Pattern(regexp = "active|inactive") String status,
      @RequestParam(required = false) Boolean archived,
      @RequestParam(required = false) @Min(1) @Max(200) Integer limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(q, status, archived, limit, offset);
  }

  @GetMapping("/customers/{id}")
  public Object retrieve(@PathVariable String id) {
    return queries.retrieve(id);
  }

  @GetMapping("/customers/{id}/details")
  public Object details(@PathVariable String id) {
    return queries.details(id);
  }

  @PostMapping("/catalog/customers")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(@RequestBody String body) {
    return commands.create(decoder.decode(body, SaveContact.class));
  }

  @PutMapping("/catalog/customers/{id}")
  public Object update(@PathVariable String id, @RequestBody String body) {
    return commands.update(id, decoder.decode(body, SaveContact.class));
  }

  @DeleteMapping("/customers/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void archive(@PathVariable String id) {
    commands.archive(id);
  }

  @GetMapping("/customers/{id}/rates")
  public Object rates(@PathVariable String id) {
    var c = queries.retrieve(id);
    return java.util.Map.of(
        "customerId",
        id,
        "rates",
        java.util.stream.Stream.concat(
                ((java.util.List<?>) c.get("customerRates")).stream(),
                ((java.util.List<?>) c.get("packageRates")).stream())
            .toList());
  }

  @PutMapping("/customers/{id}/rates")
  public Object rates(@PathVariable String id, @RequestBody String body) {
    return rates.replace(
        id, decoder.decode(body, com.sowad.nourish.customerrate.command.SaveCustomerRates.class));
  }
}
