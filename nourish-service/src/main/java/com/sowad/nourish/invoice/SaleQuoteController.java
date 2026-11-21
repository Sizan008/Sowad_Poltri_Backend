package com.sowad.nourish.invoice;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class SaleQuoteController {
  private final SaleQuoteService service;
  private final StrictCommandDecoder decoder;

  public SaleQuoteController(SaleQuoteService service, StrictCommandDecoder decoder) {
    this.service = service;
    this.decoder = decoder;
  }

  @PostMapping("/sales/quote")
  public Object quote(@RequestBody String body) {
    return service.quote(decoder.decode(body, SaleQuoteService.Quote.class));
  }
}
