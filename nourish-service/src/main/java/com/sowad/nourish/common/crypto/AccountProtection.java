package com.sowad.nourish.common.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccountProtection {
  private final AccountCipher cipher;
  @org.springframework.beans.factory.annotation.Autowired
  public AccountProtection(@Value("${nourish.account-data-key:}") String key,
      @Value("${nourish.account-data-key-file:}") String file) {
    this(AccountKeyLoader.load(key, file));
  }
  public AccountProtection(String key) {
    cipher = new AccountCipher(key);
  }
  public String encrypt(String value, String context) { return cipher.encrypt(value, context); }
  public String decrypt(String value, String context) { return cipher.decrypt(value, context); }
  public String lookup(String field, String value) { return cipher.lookup(field, value); }

  public static boolean numberField(String field) {
    return Set.of("walletNumber", "accountNumber", "wallet_number", "account_number").contains(field);
  }

  /** Preserves snapshot shape and non-account metadata. Does not mutate its input. */
  public JsonNode transform(JsonNode source, String context, boolean encrypt) {
    if (source == null) return null;
    JsonNode result = source.deepCopy();
    visit(result, context, encrypt);
    return result;
  }
  private void visit(JsonNode node, String context, boolean encrypt) {
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      List<String> names = new ArrayList<>(); object.fieldNames().forEachRemaining(names::add);
      for (String name : names) {
        JsonNode value = object.get(name);
        if (numberField(name) && value.isTextual() && !value.asText().isEmpty()) {
          object.put(name, encrypt ? encrypt(value.asText(), context + "/" + name)
              : decrypt(value.asText(), context + "/" + name));
        } else visit(value, context, encrypt);
      }
    } else if (node.isArray()) for (JsonNode child : node) visit(child, context, encrypt);
  }
}
