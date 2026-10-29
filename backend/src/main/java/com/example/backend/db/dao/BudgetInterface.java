package com.example.backend.db.dao;

import com.example.backend.models.Budget;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;

public interface BudgetInterface {
    void upsert(YearMonth month, long categoryId, java.math.BigDecimal amount) throws SQLException;

    boolean delete(YearMonth month, long categoryId) throws SQLException; // true ako je nesto obrisano

    List<Budget> findByMonth(YearMonth month) throws SQLException;
}