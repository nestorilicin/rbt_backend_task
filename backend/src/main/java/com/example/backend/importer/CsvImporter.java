package com.example.backend.importer;

import com.example.backend.db.dao.CategoryInterface;
import com.example.backend.db.dao.TransactionInterface;
import com.example.backend.models.Category;
import com.example.backend.models.ImportResult;
import com.example.backend.models.RowError;
import com.example.backend.models.Transaction;
import com.example.backend.rules.TransactionRules;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class CsvImporter {

    private static final int BATCH_SIZE = 1000;
    private static final int MAX_ERRORS = 1000;
    private static final List<String> REQUIRED = List.of("type", "amount", "date", "description", "category");
    //strict number format: rejects "1e3" and "12,5" before BigDecimal sees them.
    private static final Pattern AMOUNT = Pattern.compile("-?\\d+(\\.\\d+)?");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader() 
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .setIgnoreEmptyLines(false)
            .build();

    private final TransactionInterface transactions;
    private final CategoryInterface categories;

    public CsvImporter(TransactionInterface transactions, CategoryInterface categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    public ImportResult importCsv(InputStream in) throws SQLException, IOException {
        // load all categories once (lower-case name -> id) to avoid one query per row
        Map<String, Long> categoryIds = new HashMap<>();
        for (Category c : categories.findAll()) {
            categoryIds.put(key(c.getName()), c.getId());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        skipBom(reader);

        int imported = 0;
        int failed = 0;
        List<RowError> errors = new ArrayList<>();
        List<Transaction> batch = new ArrayList<>(BATCH_SIZE);

        try (CSVParser parser = open(reader)) {
            checkHeader(parser);
            int columns = parser.getHeaderNames().size();
            long row = 1; // the header is row 1, so the first data row is row 2.
            Iterator<CSVRecord> it = parser.iterator();
            try {
                while (it.hasNext()) {
                    CSVRecord rec = it.next();
                    row++;
                    if (rec.size() == 1 && rec.get(0).isEmpty()) {
                        continue; // blank lines are skipped, but still counted so reported row numbers match the file.
                    }
                    try {
                        if (rec.size() != columns) {
                            throw new IllegalArgumentException(
                                    "expected " + columns + " columns but found " + rec.size());
                        }
                        batch.add(parseRow(rec, categoryIds));
                    } catch (IllegalArgumentException e) {
                        failed++;
                        if (errors.size() < MAX_ERRORS) {
                            errors.add(new RowError(row, e.getMessage()));
                        }
                    }
                    if (batch.size() >= BATCH_SIZE) {
                        transactions.insertBatch(batch);
                        imported += batch.size();
                        batch.clear();
                    }
                }
            } catch (UncheckedIOException e) {
                // broken CSV (e.g. an unclosed quote): stop here, but keep the rows already read.
                failed++;
                if (errors.size() < MAX_ERRORS) {
                    errors.add(new RowError(row + 1, "Malformed CSV, import stopped: " + e.getCause().getMessage()));
                }
            }
        }

        if (!batch.isEmpty()) {
            transactions.insertBatch(batch);
            imported += batch.size();
        }
        // failed is the exact count; the error list is capped at MAX_ERRORS.
        return new ImportResult(imported, failed, errors, failed > errors.size());
    }

    // ---------- helpers ----------

    private Transaction parseRow(CSVRecord rec, Map<String, Long> categoryIds) {
        String type = TransactionRules.type(rec.get("type"));

        String rawAmount = rec.get("amount");
        if (!AMOUNT.matcher(rawAmount).matches()) {
            throw new IllegalArgumentException("amount is not a valid number");
        }
        BigDecimal amount = TransactionRules.amount(new BigDecimal(rawAmount));

        LocalDate date;
        try {
            date = LocalDate.parse(rec.get("date")); // strogo YYYY-MM-DD, 2026-02-30 pada
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must be a valid date in the format YYYY-MM-DD");
        }
        TransactionRules.date(date);

        String description = TransactionRules.description(rec.get("description"));

        String categoryName = rec.get("category");
        if (categoryName.isEmpty()) {
            throw new IllegalArgumentException("category is required");
        }
        Long categoryId = categoryIds.get(key(categoryName));
        if (categoryId == null) {
            throw new IllegalArgumentException("Unknown category '" + categoryName + "'");
        }
        return new Transaction(null, type, amount, date, description, categoryId);
    }

    private static CSVParser open(BufferedReader reader) {
        try {
            return FORMAT.parse(reader);
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read the CSV header: " + e.getMessage());
        }
    }

    private static void checkHeader(CSVParser parser) {
        Set<String> names = new HashSet<>();
        for (String h : parser.getHeaderNames()) {
            names.add(h.strip().toLowerCase(Locale.ROOT));
        }
        List<String> missing = new ArrayList<>(REQUIRED);
        missing.removeAll(names);
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CSV header must contain the columns " + REQUIRED + " (missing: " + missing + ")");
        }
    }

    // removes the UTF-8 byte order mark that Excel adds to CSV files.
    private static void skipBom(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != 0xFEFF) {
            reader.reset();
        }
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}