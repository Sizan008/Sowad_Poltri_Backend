package com.sowad.nourish.expense;

import com.sowad.nourish.expense.command.SaveExpense;
import java.util.Map;

public interface ExpenseService {
  Map<String, Object> create(String key, SaveExpense command);

  Map<String, Object> update(String id, SaveExpense command);

  Map<String, Object> delete(String id, Long expectedRevision);
}
