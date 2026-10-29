package com.example.backend.db.dao;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.example.backend.models.Transaction;
import com.example.backend.models.TransactionFilter;

@Repository
public class TransactionRepository implements TransactionInterface {

    private static final String COLUMNS = "id, type, amount, date, description, category_id";

    private final DataSource dataSource;

    public TransactionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Transaction insert(Transaction t) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(
                        "insert into app_transaction (type, amount, date, description, category_id) values (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);

        ) {
            stm.setString(1, t.getType());
            stm.setBigDecimal(2, t.getAmount());
            stm.setObject(3, t.getDate());
            stm.setString(4, t.getDescription());
            stm.setLong(5, t.getCategoryId());
            stm.executeUpdate();
            try (ResultSet keys = stm.getGeneratedKeys()) {
                keys.next();
                return new Transaction(
                        keys.getLong(1), t.getType(), t.getAmount(), t.getDate(), t.getDescription(),
                        t.getCategoryId());
            }
        }
    }

    @Override
    public Transaction findById(long id) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM app_transaction WHERE id = ?")) {
            stm.setLong(1, id);
            try (ResultSet rs = stm.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public void update(long id, Transaction t) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(
                        "UPDATE app_transaction SET type=?, amount=?, date=?, description=?, category_id=? WHERE id=?")) {
            stm.setString(1, t.getType());
            stm.setBigDecimal(2, t.getAmount());
            stm.setObject(3, t.getDate());
            stm.setString(4, t.getDescription());
            stm.setLong(5, t.getCategoryId());
            stm.setLong(6, id);
            stm.executeUpdate();
        }
    }

    @Override
    public void delete(long id) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement("DELETE FROM app_transaction WHERE id = ?")) {
            stm.setLong(1, id);
            stm.executeUpdate();
        }
    }

    @Override
    public List<Transaction> search(TransactionFilter f, int page, int size) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM app_transaction" + where(f, params)
                + " ORDER BY date DESC, id DESC LIMIT ? OFFSET ?";
        params.add(size);
        params.add((long) page * size);

        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(sql)) {
            bind(stm, params);
            try (ResultSet rs = stm.executeQuery()) {
                List<Transaction> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        }
    }

    @Override
    public long count(TransactionFilter f) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM app_transaction" + where(f, params);
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(sql)) {
            bind(stm, params);
            try (ResultSet rs = stm.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---------- pomocne ----------

    // gradi WHERE samo od filtera koji su poslati; vrednosti idu kao parametri
    // (nema SQL injection-a)
    private static String where(TransactionFilter f, List<Object> params) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (f.from() != null) {
            sb.append(" AND date >= ?");
            params.add(f.from());
        }
        if (f.to() != null) {
            sb.append(" AND date <= ?");
            params.add(f.to());
        }
        if (f.categoryId() != null) {
            sb.append(" AND category_id = ?");
            params.add(f.categoryId());
        }
        if (f.type() != null) {
            sb.append(" AND type = ?");
            params.add(f.type());
        }
        if (f.q() != null) {
            // COLLATE: velika/mala slova se ignorisu, bez obzira na kolaciju tabele
            sb.append(" AND description LIKE ? COLLATE utf8mb4_0900_as_ci");
            params.add("%" + escapeLike(f.q()) + "%");
        }
        return sb.toString();
    }

    // % i _ iz korisnickog unosa postaju obicni znakovi (MySQL podrazumevani escape
    // znak je \)
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void bind(PreparedStatement stm, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stm.setObject(i + 1, params.get(i));
        }
    }

    private static Transaction map(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                rs.getString("type"),
                rs.getBigDecimal("amount"),
                rs.getObject("date", LocalDate.class),
                rs.getString("description"),
                rs.getLong("category_id"));
    }
}
