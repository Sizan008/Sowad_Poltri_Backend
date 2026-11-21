package com.sowad.nourish.customer;

import java.util.*;

public interface CustomerQueryService {
  List<Map<String, Object>> list(
      String q, String status, Boolean archived, Integer limit, int offset);

  Map<String, Object> retrieve(String id);

  Map<String, Object> details(String id);
}
