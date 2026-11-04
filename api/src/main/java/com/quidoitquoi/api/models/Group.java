package com.quidoitquoi.api.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank(message = "{validation.group-name.required}")
    @Size(max = 100, message = "{validation.group-name.max}")
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 1000, message = "{validation.description.max}")
    @Column(length = 1000)
    private String description;

    @Size(max = 2048, message = "{validation.image-url.max}")
    @Column(name = "img", length = 2048)
    private String img;

    @NotBlank(message = "{validation.currency.required}")
    @Pattern(regexp = "^[A-Z]{3}$", message = "{validation.currency.iso}")
    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne
    @JoinColumn(name = "admin_id")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private User admin;

    @OneToMany(mappedBy = "group")
    @JsonIgnore
    private List<Member> members;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Group() {
        // Required by JPA.
    }

    public Group(String name, String description, String img, String currency, User admin) {
        this.name = name;
        this.description = description;
        this.img = img;
        this.currency = currency;
        this.admin = admin;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public User getAdmin() {
        return admin;
    }

    public void setAdmin(User admin) {
        this.admin = admin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
