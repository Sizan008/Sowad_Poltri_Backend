package com.sowad.nourish.catalog;

public class CatalogFailure extends RuntimeException {
  private final int status;

  public CatalogFailure(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static CatalogFailure missing() {
    return new CatalogFailure(404, "Catalog record not found.");
  }

  public static CatalogFailure conflict(String message) {
    return new CatalogFailure(409, message);
  }
}
