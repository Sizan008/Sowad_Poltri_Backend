package com.sowad.nourish.user.repository.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "app_role", schema = "nourish")
public class RoleEntity {
    @Id
    @Column(length = 20)
    private String code;

    public RoleEntity() {}

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        this.code = value;
    }
}
