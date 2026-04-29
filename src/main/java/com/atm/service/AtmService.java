package com.atm.service;

import com.atm.entity.Account;
import com.atm.entity.Transaction;
import com.atm.repository.AccountRepository;
import com.atm.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class AtmService {
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @PostConstruct
    public void init() {
        if (accountRepository.count() == 0) {
            accountRepository.save(new Account(1000.0)); // Initial balance
        }
    }

    public Double getBalance() {
        return accountRepository.findById(1L).get().getBalance();
    }

    public synchronized Double deposit(Double amount) {
        Account account = accountRepository.findById(1L).get();
        double newBalance = account.getBalance() + amount;
        account.setBalance(newBalance);
        accountRepository.save(account);
        transactionRepository.save(new Transaction("Deposit", amount, newBalance));
        return newBalance;
    }

    public synchronized Double withdraw(Double amount) throws Exception {
        Account account = accountRepository.findById(1L).get();
        if (amount > account.getBalance()) {
            throw new Exception("Insufficient funds");
        }
        double newBalance = account.getBalance() - amount;
        account.setBalance(newBalance);
        accountRepository.save(account);
        transactionRepository.save(new Transaction("Withdrawal", amount, newBalance));
        return newBalance;
    }

    public List<Transaction> getTransactions() {
        return transactionRepository.findAllByOrderByTimestampDesc();
    }
}
