package com.example.backend.controllers;

import com.example.backend.db.dao.BudgetInterface;
import com.example.backend.db.dao.CategoryInterface;
import com.example.backend.models.Budget;
import com.example.backend.models.BudgetRequest;
import com.example.backend.rules.MonthRules;
import com.example.backend.rules.TransactionRules;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetInterface repo;
    private final CategoryInterface categories;

    public BudgetController(BudgetInterface repo, CategoryInterface categories) {
        this.repo = repo;
        this.categories = categories;
    }

    // postavi ili promeni budzet (isti poziv, idempotentno)
    @PutMapping("/{month}/{categoryId}")
    public Budget set(@PathVariable String month, @PathVariable long categoryId,
            @RequestBody BudgetRequest in) throws SQLException {
        YearMonth ym = parseMonth(month);
        BigDecimal amount;
        try {
            amount = TransactionRules.amount(in.amount());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (categories.findById(categoryId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category " + categoryId + " not found");
        }
        repo.upsert(ym, categoryId, amount);
        return new Budget(ym.toString(), categoryId, amount);
    }

    @DeleteMapping("/{month}/{categoryId}")
    public ResponseEntity<Void> remove(@PathVariable String month, @PathVariable long categoryId)
            throws SQLException {
        YearMonth ym = parseMonth(month);
        if (!repo.delete(ym, categoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No budget for category " + categoryId + " in " + ym);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<Budget> list(@RequestParam String month) throws SQLException {
        return repo.findByMonth(parseMonth(month));
    }

    private static YearMonth parseMonth(String month) {
        try {
            return MonthRules.parse(month);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}