package com.example.rest1.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String email;

    @JsonIgnore
    @Column(name = "password_hash")
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Role role = Role.USER;

    @Column(nullable = false)
    public boolean enabled = false;
}