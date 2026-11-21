package com.sowad.nourish.customer.repository.entity;

import com.sowad.nourish.contact.repository.entity.ContactEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "customer", schema = "nourish")
public class CustomerEntity extends ContactEntity {}
