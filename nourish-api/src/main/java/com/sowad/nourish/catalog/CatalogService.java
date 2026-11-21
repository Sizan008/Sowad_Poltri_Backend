package com.sowad.nourish.catalog;

import com.sowad.nourish.catalog.command.*;
import java.util.Map;

public interface CatalogService {
  Map<String, Object> createProduct(SaveProduct command);

  Map<String, Object> editProduct(String id, SaveProduct command);

  void archiveProduct(String id);

  Map<String, Object> createUnit(SaveUnit command);

  Map<String, Object> editUnit(String id, SaveUnit command);

  void deleteUnit(String id);

  Map<String, Object> createPackaging(String productId, SavePackaging command);

  void deactivatePackaging(String productId, String id);
}
