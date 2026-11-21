package com.sowad.nourish.receivingaccount;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.receivingaccount.command.SaveReceivingAccount;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class ReceivingAccountController {
  private final ReceivingAccountService commands;
  private final ReceivingAccountQueryService queries;
  private final StrictCommandDecoder decoder;

  public ReceivingAccountController(
      ReceivingAccountService commands,
      ReceivingAccountQueryService queries,
      StrictCommandDecoder decoder) {
    this.commands = commands;
    this.queries = queries;
    this.decoder = decoder;
  }

  @GetMapping("/receivingAccounts")
  public Object list(
      @RequestParam(required = false) @Size(max = 120) String q,
      @RequestParam(required = false) @Pattern(regexp = "bank|mfs") String kind,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) @Min(1) @Max(200) Integer limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(q, kind, active, limit, offset);
  }

  @GetMapping("/receivingAccounts/{id}")
  public Object retrieve(@PathVariable String id) {
    return queries.retrieve(id);
  }

  @PostMapping("/transactions/receiving-accounts")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(@RequestBody String body) {
    return commands.create(decoder.decode(body, SaveReceivingAccount.class));
  }

  @PutMapping("/transactions/receiving-accounts/{id}")
  public Object update(@PathVariable String id, @RequestBody String body) {
    return commands.update(id, decoder.decode(body, SaveReceivingAccount.class));
  }

  @DeleteMapping("/transactions/receiving-accounts/{id}")
  public Object deactivate(@PathVariable String id) {
    return commands.deactivate(id);
  }
}
