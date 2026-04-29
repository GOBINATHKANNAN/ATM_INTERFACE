package com.atm.controller;

import com.atm.entity.Transaction;
import com.atm.service.AtmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AtmController {
    @Autowired
    private AtmService atmService;

    @GetMapping("/balance")
    public Map<String, Double> getBalance() {
        return Map.of("balance", atmService.getBalance());
    }

    @PostMapping("/deposit")
    public Map<String, Double> deposit(@RequestBody Map<String, Double> payload) {
        Double amount = payload.get("amount");
        return Map.of("balance", atmService.deposit(amount));
    }

    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody Map<String, Double> payload) {
        Double amount = payload.get("amount");
        try {
            return Map.of("balance", atmService.withdraw(amount));
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @GetMapping("/transactions")
    public List<Transaction> getTransactions() {
        return atmService.getTransactions();
    }
}
