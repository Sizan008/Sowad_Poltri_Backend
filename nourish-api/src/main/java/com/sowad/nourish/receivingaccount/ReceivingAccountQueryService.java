package com.sowad.nourish.receivingaccount;

import java.util.*;

public interface ReceivingAccountQueryService {
  List<Map<String, Object>> list(String q, String kind, Boolean active, Integer limit, int offset);

  Map<String, Object> retrieve(String id);
}
