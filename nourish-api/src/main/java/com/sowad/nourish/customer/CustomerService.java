package com.sowad.nourish.customer;

import com.sowad.nourish.contact.command.SaveContact;
import java.util.Map;

public interface CustomerService {
  Map<String, Object> create(SaveContact command);

  Map<String, Object> update(String id, SaveContact command);

  void archive(String id);
}
