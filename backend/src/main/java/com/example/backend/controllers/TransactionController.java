package com.example.backend.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.db.dao.CategoryInterface;
import com.example.backend.db.dao.TransactionInterface;
import com.example.backend.models.PageResponse;
import com.example.backend.models.Transaction;
import com.example.backend.models.TransactionFilter;
import com.example.backend.rules.TransactionRules;

import java.net.URI;
import java.sql.SQLException;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private static final int MAX_SIZE = 100;

    private final TransactionInterface repo;
    private final CategoryInterface categories;

    public TransactionController(TransactionInterface repo, CategoryInterface categories) {
        this.repo = repo;
        this.categories = categories;
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@RequestBody Transaction in) throws SQLException {
        Transaction clean = validate(in);
        Transaction created = repo.insert(clean);
        return ResponseEntity.created(URI.create("/transactions/" + created.getId())).body(created);
    }

    @GetMapping("/{id}")
    public Transaction get(@PathVariable long id) throws SQLException {
        return getOr404(id);
    }

    @PutMapping("/{id}")
    public Transaction update(@PathVariable long id, @RequestBody Transaction in) throws SQLException {
        Transaction clean = validate(in);
        getOr404(id);
        repo.update(id, clean);
        clean.setId(id);
        return clean;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) throws SQLException {
        getOr404(id);
        repo.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResponse<Transaction> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws SQLException {

        if (page < 0) {
            throw bad("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw bad("size must be between 1 and " + MAX_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw bad("from must not be after to");
        }
        String typeFilter = null;
        if (type != null && !type.isBlank()) {
            try {
                typeFilter = TransactionRules.type(type);
            } catch (IllegalArgumentException e) {
                throw bad(e.getMessage());
            }
        }
        String text = (q == null || q.isBlank()) ? null : q.strip();

        TransactionFilter filter = new TransactionFilter(from, to, categoryId, typeFilter, text);
        long total = repo.count(filter);
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(repo.search(filter, page, size), page, size, total, totalPages);
    }
    // ---------- pomocne ----------

    private Transaction getOr404(long id) throws SQLException {
        Transaction t = repo.findById(id);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction " + id + " not found");
        }
        return t;
    }

    // vraca ociscenu kopiju; klijentov id se ignorise
    private Transaction validate(Transaction in) throws SQLException {
        String type;
        java.math.BigDecimal amount;
        String description;
        try {
            type = TransactionRules.type(in.getType());
            amount = TransactionRules.amount(in.getAmount());
            description = TransactionRules.description(in.getDescription());
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
        if (in.getDate() == null) {
            throw bad("date is required");
        }
        if (in.getCategoryId() == null) {
            throw bad("categoryId is required");
        }
        if (categories.findById(in.getCategoryId()) == null) {
            throw bad("Category " + in.getCategoryId() + " does not exist");
        }
        return new Transaction(null, type, amount, in.getDate(), description, in.getCategoryId());
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

}
