package com.sowad.nourish.receivingaccount;

import static com.sowad.nourish.common.masterdata.MasterDataException.require;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.contact.ContactRules;
import com.sowad.nourish.receivingaccount.command.SaveReceivingAccount;
import com.sowad.nourish.receivingaccount.repository.entity.ReceivingAccountEntity;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ReceivingAccountRules {
  private final com.sowad.nourish.common.crypto.AccountProtection protection;
  public ReceivingAccountRules(com.sowad.nourish.common.crypto.AccountProtection protection) { this.protection = protection; }

  private String text(String value) {
    return ContactRules.text(value).replaceAll("\\s+", " ");
  }

  public void apply(ReceivingAccountEntity entity, SaveReceivingAccount c) {
    if (entity.getKind() != null && !entity.getKind().equals(c.kind()))
      throw MasterDataException.conflict(
          "Account kind cannot change. Register a separate account.");
    require(text(c.label()).length() >= 2, "Account label needs at least two characters.");
    entity.setKind(c.kind());
    entity.setLabel(text(c.label()));
    entity.setActive(c.active());
    if (c.kind().equals("mfs")) {
      require(Set.of("bkash", "nagad").contains(text(c.provider())), "Choose bKash or Nagad.");
      require(
          text(c.bankName()).isEmpty()
              && text(c.accountNumber()).isEmpty()
              && text(c.accountName()).isEmpty(),
          "MFS accounts cannot contain bank fields.");
      entity.setProvider(text(c.provider()));
      entity.setWalletNumber(ContactRules.phone(c.walletNumber(), true));
      entity.setBankName(null);
      entity.setAccountNumber(null);
      entity.setAccountName(null);
    } else {
      require(
          text(c.provider()).isEmpty() && text(c.walletNumber()).isEmpty(),
          "Bank accounts cannot contain MFS fields.");
      require(
          text(c.bankName()).length() >= 2 && text(c.accountName()).length() >= 2,
          "Bank name and account holder name are required.");
      String number = text(c.accountNumber()).replaceAll("[ /-]", "").toUpperCase(Locale.ROOT);
      require(
          number.matches("[A-Z0-9]{4,40}"),
          "Use a bank account number of 4–40 English letters/digits.");
      entity.setBankName(text(c.bankName()));
      entity.setAccountNumber(number);
      entity.setAccountName(text(c.accountName()));
      entity.setProvider(null);
      entity.setWalletNumber(null);
    }
    String wallet = entity.getWalletNumber(), bank = entity.getAccountNumber();
    entity.setWalletNumberLookup(protection.lookup("wallet", wallet));
    entity.setAccountNumberLookup(protection.lookup("bank", bank));
    entity.setWalletNumber(protection.encrypt(wallet, "receiving/" + entity.getId() + "/wallet"));
    entity.setAccountNumber(protection.encrypt(bank, "receiving/" + entity.getId() + "/bank"));
    entity.setUpdatedAt(Instant.now());
  }
}
