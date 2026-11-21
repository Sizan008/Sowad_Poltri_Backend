package com.sowad.nourish.catalog;

import java.util.*;

public interface CatalogQueryService {
  List<Map<String, Object>> categories();

  List<Map<String, Object>> units();

  Map<String, Object> unit(String id);

  List<Map<String, Object>> products(Map<String, String> filters);

  Map<String, Object> product(String id);

  List<Map<String, Object>> feedCatalog();

  Map<String, Object> options();

  Map<String, Object> cascade(Map<String, String> filters);

  List<Map<String, Object>> packaging(String productId);
}
