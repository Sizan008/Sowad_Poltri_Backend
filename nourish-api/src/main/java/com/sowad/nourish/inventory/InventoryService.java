package com.sowad.nourish.inventory;

public interface InventoryService {
  java.util.Map<String, Object> change(
      String requestKey, com.sowad.nourish.inventory.command.ChangeStock command);
}
