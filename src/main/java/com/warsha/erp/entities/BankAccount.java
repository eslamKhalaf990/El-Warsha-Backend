package com.warsha.erp.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "BankAccounts")
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BankAccountID")
    private Long id;

    @Column(name = "Name")
    private String name; // e.g., Vodafone Cash, CIB Bank

    @Column(name = "AccountType")
    private String accountType; // Wallet, Bank, Post

    @Column(name = "CurrentBalance", nullable = false)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "IsOwnerSafe")
    private boolean isOwnerSafe = false;

    // Never sent to clients
    @JsonIgnore
    @Column(name = "HashedPassword")
    private String hashedPassword;
}
