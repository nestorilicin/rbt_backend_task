package com.example.backend.db.dao;

import com.example.backend.models.CategorySummary;
import com.example.backend.models.Totals;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;

public interface SummaryInterface {
    Totals totals(YearMonth month) throws SQLException;

    List<CategorySummary> categories(YearMonth month) throws SQLException;
}