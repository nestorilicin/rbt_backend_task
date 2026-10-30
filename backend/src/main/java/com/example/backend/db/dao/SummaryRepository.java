package com.example.backend.db.dao;

import com.example.backend.models.CategorySummary;
import com.example.backend.models.Totals;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SummaryRepository implements SummaryInterface {

    private final DataSource dataSource;

    public SummaryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Totals totals(YearMonth month) throws SQLException {
        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN type = 'INCOME'  THEN amount END), 0), "
                + "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount END), 0) "
                + "FROM app_transaction WHERE date >= ? AND date <= ?";
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(sql)) {
            stm.setObject(1, month.atDay(1));
            stm.setObject(2, month.atEndOfMonth());
            try (ResultSet rs = stm.executeQuery()) {
                rs.next();
                return new Totals(rs.getBigDecimal(1), rs.getBigDecimal(2));
            }
        }
    }

    // a category is included if it has an expense (s) OR a budget (b) in the month.
    // income is never counted as spent. Sorted by spent, then name.
    @Override
    public List<CategorySummary> categories(YearMonth month) throws SQLException {
        String sql = "SELECT c.id, c.name, COALESCE(s.spent, 0) AS spent, b.amount AS budget "
                + "FROM category c "
                + "LEFT JOIN (SELECT category_id, SUM(amount) AS spent FROM app_transaction "
                + "           WHERE type = 'EXPENSE' AND date >= ? AND date <= ? "
                + "           GROUP BY category_id) s ON s.category_id = c.id "
                + "LEFT JOIN budget b ON b.category_id = c.id AND b.month = ? "
                + "WHERE s.category_id IS NOT NULL OR b.id IS NOT NULL "
                + "ORDER BY spent DESC, c.name";
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(sql)) {
            stm.setObject(1, month.atDay(1));
            stm.setObject(2, month.atEndOfMonth()); // Date range uses the last day of the month, so there is no "next month" calculation.
            stm.setObject(3, month.atDay(1));
            try (ResultSet rs = stm.executeQuery()) {
                List<CategorySummary> result = new ArrayList<>();
                while (rs.next()) {
                    BigDecimal spent = rs.getBigDecimal("spent");
                    BigDecimal budget = rs.getBigDecimal("budget"); // no budget means null; otherwise remaining can be negative when over budget.
                    BigDecimal remaining = budget == null ? null : budget.subtract(spent); 
                    result.add(new CategorySummary(rs.getLong("id"), rs.getString("name"),
                            spent, budget, remaining));
                }
                return result;
            }
        }
    }
}