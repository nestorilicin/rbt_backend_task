package com.example.backend.db.dao;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.example.backend.models.Category;

@Repository
public class CategoryRepository implements CategoryInterface {

    private final DataSource dataSource;

    public CategoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean existsByName(String name) throws SQLException {
        try (
                Connection con = dataSource.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM category WHERE name = ?");

        ) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    @Override
    public Category insert(String name, String color) throws SQLException {
        try (
                Connection con = dataSource.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO category (name, color) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS);) {
            ps.setString(1, name);
            ps.setString(2, color);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return new Category(keys.getLong(1), name, color);

        }
    }

    @Override
    public Category findById(long id) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement("SELECT id, name, color FROM category WHERE id = ?")) {
            stm.setLong(1, id);
            try (ResultSet rs = stm.executeQuery()) {
                if (rs.next()) {
                    return new Category(rs.getLong("id"), rs.getString("name"), rs.getString("color"));
                }
                return null;
            }
        }
    }

    @Override
    public boolean existsByNameAndIdNot(String name, long id) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con
                        .prepareStatement("SELECT COUNT(*) FROM category WHERE name = ? AND id <> ?")) {
            stm.setString(1, name);
            stm.setLong(2, id);
            try (ResultSet rs = stm.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    @Override
    public void update(long id, String name, String color) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement("UPDATE category SET name = ?, color = ? WHERE id = ?")) {
            stm.setString(1, name);
            stm.setString(2, color);
            stm.setLong(3, id);
            stm.executeUpdate();
        }
    }

    @Override
    public List<Category> findAll() throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement("SELECT id, name, color FROM category ORDER BY name");
                ResultSet rs = stm.executeQuery()) {
            List<Category> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new Category(rs.getLong("id"), rs.getString("name"), rs.getString("color")));
            }
            return result;
        }
    }

    @Override
    public boolean hasTransactions(long id) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement(
                        "SELECT COUNT(*) FROM app_transaction WHERE category_id = ?")) {
            stm.setLong(1, id);
            try (ResultSet rs = stm.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    @Override
    public void delete(long id) throws SQLException {
        try (Connection con = dataSource.getConnection();
                PreparedStatement stm = con.prepareStatement("DELETE FROM category WHERE id = ?")) {
            stm.setLong(1, id);
            stm.executeUpdate();
        }
    }

}
