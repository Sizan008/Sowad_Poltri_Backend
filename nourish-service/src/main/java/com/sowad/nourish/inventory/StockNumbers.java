package com.sowad.nourish.inventory;

import com.sowad.nourish.common.masterdata.MasterDataException;
import java.math.*;

public final class StockNumbers {
  private StockNumbers() {}

  public static void require(boolean ok, String message) {
    MasterDataException.require(ok, message);
  }

  public static BigDecimal decimal(BigDecimal n, String label, int scale, boolean zero) {
    require(
        n != null
            && (zero ? n.signum() >= 0 : n.signum() > 0)
            && n.abs().compareTo(new BigDecimal("1000000000")) <= 0
            && n.stripTrailingZeros().scale() <= scale,
        label + " has an invalid value/precision (maximum 1 billion).");
    return n;
  }

  public static BigDecimal rate(BigDecimal n, String label) {
    return n == null ? null : decimal(n, label, 4, false);
  }

  public static BigDecimal money(BigDecimal n) {
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  public static String text(String s, int max, String label) {
    s = s == null ? "" : s.trim();
    require(s.length() <= max, label + " is too long.");
    return s;
  }

  public static BigDecimal factor(String base, String given) {
    if (given == null || given.equals(base)) return BigDecimal.ONE;
    if ((base.equals("g") && given.equals("kg")) || (base.equals("ml") && given.equals("l")))
      return new BigDecimal("1000");
    throw new MasterDataException(400, "Rate/measurement unit does not match product base unit.");
  }

  public static String key(BigDecimal n) {
    return n.stripTrailingZeros().toPlainString();
  }
}
