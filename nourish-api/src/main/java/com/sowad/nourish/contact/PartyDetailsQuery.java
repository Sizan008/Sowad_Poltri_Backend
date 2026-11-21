package com.sowad.nourish.contact;

import java.util.Map;

public interface PartyDetailsQuery {
  Map<String, Object> supplier(String id);

  Map<String, Object> customer(String id);
}
