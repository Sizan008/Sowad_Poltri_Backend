package com.sowad.nourish.supplier.repository.entity;

import com.sowad.nourish.contact.repository.entity.ContactEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "supplier", schema = "nourish")
public class SupplierEntity extends ContactEntity {}
