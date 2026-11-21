package com.sowad.nourish.inventory;

public interface StockPolicy {
  void requireEmptyProduct(String id);

  void requireEmptyPackaging(String id);
}
