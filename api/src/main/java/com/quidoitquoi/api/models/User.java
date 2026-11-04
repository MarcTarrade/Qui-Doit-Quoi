package com.quidoitquoi.api.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank(message = "{validation.username.required}")
    @Size(max = 50, message = "{validation.username.max}")
    @Column(nullable = false, unique = false, length = 50)
    private String username;

    @NotBlank(message = "{validation.lastname.required}")
    @Size(max = 100, message = "{validation.lastname.max}")
    @Column(nullable = false, unique = false, length = 100)
    private String lastname;

    @NotBlank(message = "{validation.firstname.required}")
    @Size(max = 100, message = "{validation.firstname.max}")
    @Column(nullable = false, unique = false, length = 100)
    private String firstname;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Size(max = 2048, message = "{validation.image-url.max}")
    @Column(name = "img", length = 2048)
    private String img;

    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @JsonIgnore
    @OneToMany(mappedBy = "admin")
    private List<Group> groups = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
        // Required by JPA.
    }

    public User(String username, String email, String img, String lastname, String firstname) {
        this.username = username;
        this.email = email;
        this.img = img;
        this.lastname = lastname;
        this.firstname = firstname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}