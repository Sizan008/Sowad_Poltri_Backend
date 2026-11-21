package com.sowad.nourish.report;

import static com.sowad.nourish.inventory.StockNumbers.require;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

public record ReportPeriod(LocalDate from, LocalDate to) {
  public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

  public static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  public static ReportPeriod of(LocalDate from, LocalDate to, boolean defaultRange) {
    require((from == null) == (to == null), "Supply both from and to dates, or neither.");
    if (from == null && defaultRange) {
      to = today();
      from = to.minusDays(29);
    }
    if (from != null)
      require(
          !from.isBefore(LocalDate.of(2000, 1, 1))
              && !to.isAfter(LocalDate.of(2099, 12, 31))
              && !from.isAfter(to)
              && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 3659,
          "Date range must be ordered, within 2000-2099 and at most 3660 days.");
    return new ReportPeriod(from, to);
  }

  public Timestamp start() {
    return from == null ? null : Timestamp.from(from.atStartOfDay(ZONE).toInstant());
  }

  public Timestamp end() {
    return to == null ? null : Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());
  }

  public Map<String, Object> metadata() {
    var m = new LinkedHashMap<String, Object>();
    m.put("from", from);
    m.put("to", to);
    m.put("timeZone", ZONE.toString());
    return m;
  }

  public static String search(String q) {
    if (q == null || q.isBlank()) return null;
    require(q.length() <= 120, "Search must be at most 120 characters.");
    return "%" + q.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
