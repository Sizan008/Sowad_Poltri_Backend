package com.sowad.nourish.common.crypto;

import org.springframework.beans.factory.InitializingBean;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountKeyGuard implements InitializingBean {
  private final JdbcTemplate jdbc;
  private final AccountProtection protection;
  public AccountKeyGuard(JdbcTemplate jdbc, AccountProtection protection) {
    this.jdbc = jdbc; this.protection = protection;
  }
  @Override public void afterPropertiesSet() {
    try {
      String value = jdbc.queryForObject("SELECT key_check FROM nourish.account_crypto_state WHERE id=1", String.class);
      if (!"nourish-account-key-v1".equals(protection.decrypt(value, "key-check")))
        throw new IllegalStateException();
    } catch (Exception e) {
      throw new IllegalStateException("Account encryption migration/key verification required. Run scripts/account-encryption.sh verify with the original ACCOUNT_DATA_KEY before starting.");
    }
  }
}
