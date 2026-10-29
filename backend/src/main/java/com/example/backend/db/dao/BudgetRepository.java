package com.example.backend.db.dao;

import com.example.backend.models.Budget;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BudgetRepository implements BudgetInterface {

    private final DataSource dataSource;

    public BudgetRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // UNIQUE (category_id, month) u bazi: prvi put ubaci, svaki sledeci put izmeni
    // iznos
    @Override
    public void upsert(YearMonth month, long categoryId, BigDecimal amount) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(
                        "INSERT INTO budget (month, category_id, amount) VALUES (?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE amount = VALUES(amount)")) {
            stm.setObject(1, month.atDay(1));
            stm.setLong(2, categoryId);
            stm.setBigDecimal(3, amount);
            stm.executeUpdate();
        }
    }

    @Override
    public boolean delete(YearMonth month, long categoryId) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(
                        "DELETE FROM budget WHERE month = ? AND category_id = ?")) {
            stm.setObject(1, month.atDay(1));
            stm.setLong(2, categoryId);
            return stm.executeUpdate() > 0;
        }
    }

    @Override
    public List<Budget> findByMonth(YearMonth month) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(
                        "SELECT month, category_id, amount FROM budget WHERE month = ? ORDER BY category_id")) {
            stm.setObject(1, month.atDay(1));
            try (ResultSet rs = stm.executeQuery()) {
                List<Budget> result = new ArrayList<>();
                while (rs.next()) {
                    YearMonth m = YearMonth.from(rs.getObject("month", LocalDate.class));
                    result.add(new Budget(m.toString(), rs.getLong("category_id"), rs.getBigDecimal("amount")));
                }
                return result;
            }
        }
    }
}