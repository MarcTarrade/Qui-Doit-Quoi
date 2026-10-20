package com.quidoitquoi.api.models;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_logs")
public class GroupLog {

    public static final String EXPENSE_ADDED = "EXPENSE_ADDED";
    public static final String GROUP_UPDATED = "GROUP_UPDATED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String type;

    @CreationTimestamp
    @Column(name = "date", nullable = false, updatable = false)
    private Instant date;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    @JsonIgnore
    private Group group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "made_by", nullable = false)
    @JsonIgnore
    private User madeBy;

    protected GroupLog() {
        // Required by JPA.
    }

    public GroupLog(String type, Group group, User madeBy) {
        this.type = type;
        this.group = group;
        this.madeBy = madeBy;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Instant getDate() {
        return date;
    }

    public Group getGroup() {
        return group;
    }

    public User getMadeBy() {
        return madeBy;
    }
}
