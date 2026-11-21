package com.sowad.nourish.invoice;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SalesSupport {
  private final ObjectMapper json;

  public SalesSupport(ObjectMapper json) {
    this.json = json;
  }

  public String encode(Object v) {
    try {
      return json.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Object decode(String s) {
    try {
      return json.readValue(s, Object.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static BigDecimal moneyInput(BigDecimal n, String label, boolean zero) {
    return decimal(n, label, 2, zero);
  }

  public static void date(Instant date, String label) {
    require(
        date != null
            && !date.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
            && date.isBefore(Instant.now().plusSeconds(86400)),
        label + " must be from 2000 through today (one day timezone allowance).");
  }
}
