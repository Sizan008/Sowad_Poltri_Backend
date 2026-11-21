package com.sowad.nourish.customer;

import java.util.Map;

public interface CustomerFinancialQuery {
  Map<String, Object> summary(String customerId);
}
