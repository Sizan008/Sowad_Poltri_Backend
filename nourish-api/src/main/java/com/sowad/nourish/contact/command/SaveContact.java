package com.sowad.nourish.contact.command;

import jakarta.validation.constraints.*;

/** Server generates id, name/area aliases and timestamps. Rates have a separate command. */
public record SaveContact(
    @NotBlank @Size(min = 2, max = 120) String nameEn,
    @Size(max = 120) String nameBn,
    @NotBlank @Size(min = 2, max = 240) String addressEn,
    @Size(max = 240) String addressBn,
    @NotBlank @Size(max = 30) String phone,
    @Size(max = 30) String whatsappPhone,
    @NotNull @Pattern(regexp = "active|inactive") String status,
    @Size(max = 500) String note,
    @Size(max = 500) String lastNote) {}
