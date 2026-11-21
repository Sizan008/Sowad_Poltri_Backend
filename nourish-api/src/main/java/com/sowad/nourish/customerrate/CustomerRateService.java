package com.sowad.nourish.customerrate;

import com.sowad.nourish.customerrate.command.SaveCustomerRates;
import java.util.Map;

public interface CustomerRateService {
  Map<String, Object> replace(String customerId, SaveCustomerRates command);
}
