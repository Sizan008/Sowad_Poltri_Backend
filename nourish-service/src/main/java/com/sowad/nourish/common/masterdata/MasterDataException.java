package com.sowad.nourish.common.masterdata;

public class MasterDataException extends RuntimeException {
  private final int status;

  public MasterDataException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static MasterDataException missing(String entity) {
    return new MasterDataException(404, entity + " not found.");
  }

  public static MasterDataException conflict(String message) {
    return new MasterDataException(409, message);
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new MasterDataException(400, message);
  }
}
