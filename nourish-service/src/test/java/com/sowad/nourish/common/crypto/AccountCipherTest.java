package com.sowad.nourish.common.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;

class AccountCipherTest {
  private final String key = Base64.getEncoder().encodeToString(new byte[32]);
  @Test void roundTripNonceAndLeadingZero() {
    var c = new AccountCipher(key);
    String a=c.encrypt("00123456","receiving/a/bank"),b=c.encrypt("00123456","receiving/a/bank");
    assertNotEquals(a,b);
    assertEquals("00123456",c.decrypt(a,"receiving/a/bank"));
    assertEquals("01712345678",c.decrypt(c.encrypt("01712345678","receiving/a/wallet"),"receiving/a/wallet"));
    assertNull(c.encrypt(null,"x"));assertNull(c.decrypt(null,"x"));
    assertEquals(c.lookup("bank","00123456"),c.lookup("bank","00123456"));
    assertNotEquals(c.lookup("wallet","00123456"),c.lookup("bank","00123456"));
  }
  @Test void wrongKeyTamperAndContextFailClosed() {
    var c=new AccountCipher(key);String value=c.encrypt("00123456","receiving/a/bank");
    assertThrows(IllegalStateException.class,()->c.decrypt(value,"receiving/b/bank"));
    byte[] other=new byte[32];other[0]=1;
    assertThrows(IllegalStateException.class,()->new AccountCipher(Base64.getEncoder().encodeToString(other)).decrypt(value,"receiving/a/bank"));
    byte[] bytes=Base64.getDecoder().decode(value.substring(AccountCipher.PREFIX.length()));bytes[15]^=1;
    assertThrows(IllegalStateException.class,()->c.decrypt(AccountCipher.PREFIX+Base64.getEncoder().encodeToString(bytes),"receiving/a/bank"));
    assertThrows(IllegalStateException.class,()->c.decrypt("00123456","receiving/a/bank"));
    assertThrows(IllegalStateException.class,()->new AccountCipher(""));
    assertThrows(IllegalStateException.class,()->new AccountCipher("secret"));
  }
}
