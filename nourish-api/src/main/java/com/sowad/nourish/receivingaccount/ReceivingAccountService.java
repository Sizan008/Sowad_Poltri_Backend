package com.sowad.nourish.receivingaccount;

import com.sowad.nourish.receivingaccount.command.SaveReceivingAccount;
import java.util.Map;

public interface ReceivingAccountService {
  Map<String, Object> create(SaveReceivingAccount command);

  Map<String, Object> update(String id, SaveReceivingAccount command);

  Map<String, Object> deactivate(String id);
}
