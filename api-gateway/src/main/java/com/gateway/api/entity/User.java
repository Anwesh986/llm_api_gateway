package com.gateway.api.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 50)
    private String role = "ROLE_USER";

    @Column(name = "tier", nullable = false, length = 50)
    private String tier = "FREE";

    @Column(name = "monthly_budget_usd",
            nullable = false,
            precision = 10,
            scale = 2)
    private BigDecimal monthlyBudgetUsd = new BigDecimal("50.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Required by JPA
    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    protected void onCreate() {
        normalizeEmail();

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeEmail();
        this.updatedAt = Instant.now();
    }

    private void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(Locale.ROOT);
        }
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public BigDecimal getMonthlyBudgetUsd() {
        return monthlyBudgetUsd;
    }

    public void setMonthlyBudgetUsd(BigDecimal monthlyBudgetUsd) {
        this.monthlyBudgetUsd = monthlyBudgetUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}