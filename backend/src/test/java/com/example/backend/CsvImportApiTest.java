package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsvImportApiTest {

    static final String HEADER = "type,amount,date,description,category\n";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM budget");
        jdbc.update("DELETE FROM app_transaction");
        jdbc.update("DELETE FROM category");
        jdbc.update("INSERT INTO category (name, color) VALUES ('Eating out', '#FF9800')");
        jdbc.update("INSERT INTO category (name, color) VALUES ('Other', '#607D8B')");
    }

    // ---------- helpers ----------

    private ResultActions upload(byte[] content) throws Exception {
        return mvc.perform(multipart("/transactions/import")
                .file(new MockMultipartFile("file", "t.csv", "text/csv", content)));
    }

    private ResultActions upload(String csv) throws Exception {
        return upload(csv.getBytes(StandardCharsets.UTF_8));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private ResultActions uploadOneRow(String row) throws Exception {
        return upload(HEADER + row + "\n");
    }

    // ---------- T5.1 read and validation ----------

    @Test
    void validFile_importsAllRows() throws Exception {
        upload(HEADER
                + "expense,12.50,2026-10-08,Lunch,Eating out\n"
                + "income,300.00,2026-10-09,,Other\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.errors.length()").value(0))
                .andExpect(jsonPath("$.errorsTruncated").value(false));
        assertEquals(2, count("SELECT COUNT(*) FROM app_transaction"));
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE description IS NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE type='EXPENSE' AND amount=12.50 "
                + "AND date='2026-10-08' AND description='Lunch'"));
    }

    @Test
    void categoryAndTypeAreCaseInsensitive() throws Exception {
        uploadOneRow("EXPENSE,10,2026-10-08,x,eating OUT")
                .andExpect(jsonPath("$.imported").value(1));
    }

    @Test
    void headerIsCaseInsensitive_andColumnOrderIsFree() throws Exception {
        upload("Category,Description,Date,Amount,Type\nOther,Hi,2026-10-08,5,income\n")
                .andExpect(jsonPath("$.imported").value(1));
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE type='INCOME' AND description='Hi'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.5", "12", "1000000.00", "0.01", "12.500"})
    void validAmounts_areImported(String amount) throws Exception {
        uploadOneRow("expense," + amount + ",2026-10-08,,Other")
                .andExpect(jsonPath("$.imported").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.001", "12.345", "1000000.01", "abc", "", "1e3", "12,5"})
    void invalidAmounts_areReportedWithRowNumber(String amount) throws Exception {
        uploadOneRow("expense,\"" + amount + "\",2026-10-08,,Other")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(2));
        assertEquals(0, count("SELECT COUNT(*) FROM app_transaction"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "08.10.2026", "abc", "", "10000-01-01"})
    void invalidDates_areReported(String date) throws Exception {
        uploadOneRow("expense,10," + date + ",,Other")
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"transfer", ""})
    void invalidType_isReported(String type) throws Exception {
        uploadOneRow(type + ",10,2026-10-08,,Other")
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    void descriptionLengthBoundary() throws Exception {
        upload(HEADER
                + "expense,1,2026-10-08," + "a".repeat(200) + ",Other\n"
                + "expense,1,2026-10-08," + "a".repeat(201) + ",Other\n")
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(3));
    }

    @Test
    void unknownOrEmptyCategory_isReported() throws Exception {
        upload(HEADER
                + "expense,10,2026-10-08,x,Foo\n"
                + "expense,10,2026-10-08,x,\n")
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.errors[0].message").value("Unknown category 'Foo'"));
    }

    @Test
    void wrongNumberOfColumns_isReported() throws Exception {
        upload(HEADER
                + "expense,10,2026-10-08,Lunch\n"
                + "expense,10,2026-10-08,Lunch,Other,extra\n")
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.failed").value(2));
    }

    @Test
    void descriptionWithCommaInQuotes_isKeptWhole() throws Exception {
        uploadOneRow("expense,10.00,2026-10-08,\"Lunch, with friends\",Eating out")
                .andExpect(jsonPath("$.imported").value(1));
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE description = 'Lunch, with friends'"));
    }

    @Test
    void utf8Characters_areStoredCorrectly() throws Exception {
        uploadOneRow("expense,10,2026-10-08,Ručak čšž,Other")
                .andExpect(jsonPath("$.imported").value(1));
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE description = 'Ručak čšž'"));
    }

    @Test
    void windowsLineEndings_andBom_areHandled() throws Exception {
        upload("\uFEFFtype,amount,date,description,category\r\n"
                + "expense,10,2026-10-08,A,Other\r\n"
                + "expense,20,2026-10-09,B,Other\r\n")
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void trailingBlankLines_areIgnored() throws Exception {
        upload(HEADER + "expense,10,2026-10-08,A,Other\n\n\n")
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void headerOnly_importsNothing() throws Exception {
        upload(HEADER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void missingOrWrongHeader_returns400() throws Exception {
        upload("expense,10,2026-10-08,A,Other\n").andExpect(status().isBadRequest());
        upload("type,amount,date\nexpense,10,2026-10-08\n").andExpect(status().isBadRequest());
        assertEquals(0, count("SELECT COUNT(*) FROM app_transaction"));
    }

    // ---------- T5.2 partial import and report ----------

    @Test
    void mixedFile_importsGoodRows_andReportsBadOnesWithRowNumbers() throws Exception {
        upload(HEADER
                + "expense,10,2026-10-08,ok1,Other\n"          // row 2
                + "expense,abc,2026-10-08,badAmount,Other\n"   // row 3
                + "expense,10,2026-10-08,ok2,Other\n"          // row 4
                + "expense,10,2026-10-08,badCat,Nope\n")       // row 5
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.errors[1].row").value(5));
        assertEquals(2, count("SELECT COUNT(*) FROM app_transaction"));
    }

    @Test
    void blankLinesAreCountedInRowNumbers() throws Exception {
        upload(HEADER
                + "expense,10,2026-10-08,ok,Other\n"      // row 2
                + "\n"                                     // row 3 (empty)
                + "expense,abc,2026-10-08,bad,Other\n")    // row 4
                .andExpect(jsonPath("$.errors[0].row").value(4));
    }

    @Test
    void malformedCsv_keepsGoodRowsBeforeIt() throws Exception {
        upload(HEADER
                + "expense,10,2026-10-08,ok,Other\n"
                + "expense,10,2026-10-08,\"never closed,Other\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    void manyRows_areImportedAcrossSeveralBatches() throws Exception {
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 2500; i++) {
            sb.append("expense,1.00,2026-10-08,row").append(i).append(",Other\n");
        }
        upload(sb.toString())
                .andExpect(jsonPath("$.imported").value(2500))
                .andExpect(jsonPath("$.failed").value(0));
        assertEquals(2500, count("SELECT COUNT(*) FROM app_transaction"));
    }

    @Test
    void manyBadRows_errorListIsTruncated_butFailedIsExact() throws Exception {
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 1500; i++) {
            sb.append("expense,abc,2026-10-08,x,Other\n");
        }
        sb.append("expense,5,2026-10-08,good,Other\n");
        upload(sb.toString())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(1500))
                .andExpect(jsonPath("$.errors.length()").value(1000))
                .andExpect(jsonPath("$.errorsTruncated").value(true));
    }

    @Test
    void importedRows_areVisibleInListAndSummary() throws Exception {
        upload(HEADER
                + "expense,12.50,2026-10-08,Lunch,Eating out\n"
                + "income,300.00,2026-10-09,,Other\n")
                .andExpect(jsonPath("$.imported").value(2));
        mvc.perform(get("/transactions"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].type").value("INCOME"));
        mvc.perform(get("/summary").param("month", "2026-10"))
                .andExpect(jsonPath("$.totalIncome").value(300.0))
                .andExpect(jsonPath("$.totalExpense").value(12.5))
                .andExpect(jsonPath("$.balance").value(287.5));
    }

    // ---------- T5.3 bad request ----------

    @Test
    void noFilePart_returns400() throws Exception {
        mvc.perform(multipart("/transactions/import")).andExpect(status().isBadRequest());
    }

    @Test
    void emptyFile_returns400() throws Exception {
        upload(new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    void binaryFile_returns400_notServerError() throws Exception {
        byte[] fakePng = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
        upload(fakePng).andExpect(status().isBadRequest());
    }
}