package com.example.backend.db.dao;

import java.sql.SQLException;
import java.util.List;

import com.example.backend.models.Transaction;
import com.example.backend.models.TransactionFilter;

public interface TransactionInterface {
    Transaction insert(Transaction t) throws SQLException;

    Transaction findById(long id) throws SQLException; // null ako ne postoji

    void update(long id, Transaction t) throws SQLException;

    void delete(long id) throws SQLException;

    List<Transaction> search(TransactionFilter f, int page, int size) throws SQLException;

    long count(TransactionFilter f) throws SQLException;
}
