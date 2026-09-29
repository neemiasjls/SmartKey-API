package com.smartkey.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** O hospede. Ex: Joao. */
@Entity
@Table(name = "guests")
public class Guest extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(length = 50)
    private String phone;

    protected Guest() {
        // exigido pelo JPA
    }

    public Guest(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
