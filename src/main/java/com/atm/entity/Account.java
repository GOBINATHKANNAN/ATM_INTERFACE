package com.atm.entity;

import javax.persistence.*;

@Entity
public class Account {
    @Id
    private Long id = 1L; // Single account for now
    private Double balance;

    public Account() {}
    public Account(Double balance) { this.balance = balance; }

    public Long getId() { return id; }
    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
}
