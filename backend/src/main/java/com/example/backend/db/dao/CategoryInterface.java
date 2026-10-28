package com.example.backend.db.dao;

import java.sql.SQLException;
import java.util.List;

import com.example.backend.models.Category;

public interface CategoryInterface {
    boolean existsByName(String name) throws SQLException;

    Category insert(String name, String color) throws SQLException;

    Category findById(long id) throws SQLException; // returns null if it doesn't exist

    boolean existsByNameAndIdNot(String name, long id) throws SQLException;

    void update(long id, String name, String color) throws SQLException;

    List<Category> findAll() throws SQLException;

    boolean hasTransactions(long id) throws SQLException;

    void delete(long id) throws SQLException;
}
