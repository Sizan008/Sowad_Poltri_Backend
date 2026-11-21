package com.sowad.nourish.contact;

import static com.sowad.nourish.common.masterdata.MasterDataException.require;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.contact.command.SaveContact;
import com.sowad.nourish.contact.repository.entity.ContactEntity;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ContactRules {
  public static String text(String value) {
    return value == null ? "" : value.trim();
  }

  public static String phone(String value, boolean required) {
    String phone = text(value).replaceAll("[ ()-]", "");
    if (phone.isEmpty() && !required) return "";
    if (phone.startsWith("+88")) phone = phone.substring(3);
    else if (phone.startsWith("88")) phone = phone.substring(2);
    require(
        phone.matches("01[3-9][0-9]{8}"),
        "Use a valid Bangladesh mobile number with English digits.");
    return phone;
  }

  private void english(String value, int max, String label) {
    require(
        value.length() >= 2
            && value.length() <= max
            && value.matches("[\\x20-\\x7E]+")
            && value.matches(".*[A-Za-z].*"),
        label + " must be English text.");
  }

  public void apply(ContactEntity entity, SaveContact c) {
    if (entity.getArchived())
      throw MasterDataException.conflict(
          "Archived records cannot be edited. Their history is retained.");
    String name = text(c.nameEn()), address = text(c.addressEn());
    english(name, 120, "Name");
    english(address, 240, "Address");
    require(
        c.note() == null || c.lastNote() == null || text(c.note()).equals(text(c.lastNote())),
        "Supply note or lastNote, not conflicting values.");
    entity.setNameEn(name);
    entity.setAddressEn(address);
    entity.setNameBn(text(c.nameBn()));
    entity.setAddressBn(text(c.addressBn()));
    entity.setPhone(phone(c.phone(), true));
    entity.setWhatsappPhone(phone(c.whatsappPhone(), false));
    entity.setStatus(c.status());
    if (c.note() != null || c.lastNote() != null)
      entity.setNote(text(c.note() != null ? c.note() : c.lastNote()));
    else if (entity.getNote() == null) entity.setNote("");
    entity.setUpdatedAt(Instant.now());
  }

  public void archive(ContactEntity entity) {
    entity.setArchived(true);
    entity.setStatus("inactive");
    entity.setUpdatedAt(Instant.now());
  }
}
