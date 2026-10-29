package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    long food;
    long fun;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM budget");
        jdbc.update("DELETE FROM app_transaction");
        jdbc.update("DELETE FROM category");
        food = insertCategory("Food");
        fun = insertCategory("Fun");
    }

    // ---------- pomocne metode ----------

    private long insertCategory(String name) {
        jdbc.update("INSERT INTO category (name, color) VALUES (?, '#FF0000')", name);
        return jdbc.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private long insertTx(String type, String amount, String date, String description, long categoryId) {
        jdbc.update("INSERT INTO app_transaction (type, amount, date, description, category_id) VALUES (?,?,?,?,?)",
                type, new java.math.BigDecimal(amount), date, description, categoryId);
        return jdbc.queryForObject("SELECT MAX(id) FROM app_transaction", Long.class);
    }

    private static String q(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    // amount se prosledjuje "sirov" (npr. 12.5, "abc" sa navodnicima, null)
    private static String body(String type, String amount, String date, String description, Long categoryId) {
        return "{\"type\":%s,\"amount\":%s,\"date\":%s,\"description\":%s,\"categoryId\":%s}"
                .formatted(q(type), amount, q(date), q(description), categoryId);
    }

    private String validBody() {
        return body("expense", "12.50", "2026-10-08", "Lunch", food);
    }

    private void postTx(String json, int expectedStatus) throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    private void putTx(long id, String json, int expectedStatus) throws Exception {
        mvc.perform(put("/transactions/" + id).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---------- T2.1 kreiranje ----------

    @Test
    void create_valid_returns201() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(12.5))
                .andExpect(jsonPath("$.description").value("Lunch"));
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "12.5", "12", "1000000.00", "0.01", "12.500" })
    void create_validAmounts_return201(String amount) throws Exception {
        postTx(body("expense", amount, "2026-10-08", null, food), 201);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-5", "0.001", "12.345", "1000000.01", "\"abc\"", "null" })
    void create_invalidAmounts_return400(String amount) throws Exception {
        postTx(body("expense", amount, "2026-10-08", null, food), 400);
        assertEquals(0, count("SELECT COUNT(*) FROM app_transaction"));
    }

    @Test
    void create_missingAmountField_returns400() throws Exception {
        postTx("{\"type\":\"expense\",\"date\":\"2026-10-08\",\"categoryId\":" + food + "}", 400);
    }

    @Test
    void create_invalidType_returns400() throws Exception {
        postTx(body("transfer", "10", "2026-10-08", null, food), 400);
        postTx(body(null, "10", "2026-10-08", null, food), 400);
        postTx(body("", "10", "2026-10-08", null, food), 400);
    }

    @Test
    void create_typeIsCaseInsensitive() throws Exception {
        postTx(body("InCoMe", "10", "2026-10-08", null, food), 201);
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-02-30", "2026-13-01", "08.10.2026", "abc" })
    void create_invalidDate_returns400(String date) throws Exception {
        postTx(body("expense", "10", date, null, food), 400);
    }

    @Test
    void create_missingDate_returns400() throws Exception {
        postTx(body("expense", "10", null, null, food), 400);
    }

    @Test
    void create_validLeapDay_returns201() throws Exception {
        postTx(body("expense", "10", "2028-02-29", null, food), 201);
    }

    @Test
    void create_descriptionBoundary() throws Exception {
        postTx(body("expense", "10", "2026-10-08", "a".repeat(200), food), 201);
        postTx(body("expense", "10", "2026-10-08", "a".repeat(201), food), 400);
    }

    @Test
    void create_withoutDescription_storesNull() throws Exception {
        postTx(body("expense", "10", "2026-10-08", null, food), 201);
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE description IS NULL"));
    }

    @Test
    void create_blankDescription_storesNull() throws Exception {
        postTx(body("expense", "10", "2026-10-08", "   ", food), 201);
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE description IS NULL"));
    }

    @Test
    void create_nonExistentCategory_returns400() throws Exception {
        postTx(body("expense", "10", "2026-10-08", null, 999999L), 400);
        postTx(body("expense", "10", "2026-10-08", null, null), 400);
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        postTx("{pokvaren", 400);
    }

    @Test
    void create_emptyObject_returns400() throws Exception {
        postTx("{}", 400);
    }

    // ---------- T2.2 citanje, izmena, brisanje ----------

    @Test
    void get_existing_returns200() throws Exception {
        long id = insertTx("EXPENSE", "12.50", "2026-10-08", "Lunch", food);
        mvc.perform(get("/transactions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-08"))
                .andExpect(jsonPath("$.categoryId").value(food));
    }

    @Test
    void get_nonExistent_returns404() throws Exception {
        mvc.perform(get("/transactions/999999")).andExpect(status().isNotFound());
    }

    @Test
    void get_nonNumericId_returns400() throws Exception {
        mvc.perform(get("/transactions/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void update_valid_changesAllFields() throws Exception {
        long id = insertTx("EXPENSE", "12.50", "2026-10-08", "Lunch", food);
        putTx(id, body("income", "99.99", "2026-11-01", "Salary", fun), 200);
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE id = ? AND type = 'INCOME' "
                + "AND amount = 99.99 AND date = '2026-11-01' AND description = 'Salary' AND category_id = ?", id,
                fun));
    }

    @Test
    void update_invalidInput_returns400_andKeepsOldData() throws Exception {
        long id = insertTx("EXPENSE", "12.50", "2026-10-08", "Lunch", food);
        putTx(id, body("expense", "0", "2026-10-08", "Lunch", food), 400);
        putTx(id, body("expense", "5", "2026-02-30", "Lunch", food), 400);
        assertEquals(1, count("SELECT COUNT(*) FROM app_transaction WHERE id = ? AND amount = 12.50", id));
    }

    @Test
    void update_toNonExistentCategory_returns400() throws Exception {
        long id = insertTx("EXPENSE", "12.50", "2026-10-08", "Lunch", food);
        putTx(id, body("expense", "10", "2026-10-08", null, 999999L), 400);
    }

    @Test
    void update_nonExistent_returns404() throws Exception {
        putTx(999999, validBody(), 404);
    }

    @Test
    void delete_existing_returns204_thenGetReturns404() throws Exception {
        long id = insertTx("EXPENSE", "12.50", "2026-10-08", "Lunch", food);
        mvc.perform(delete("/transactions/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/transactions/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void delete_nonExistent_returns404() throws Exception {
        mvc.perform(delete("/transactions/999999")).andExpect(status().isNotFound());
    }

    // ---------- T2.3 sortiranje i paginacija ----------

    @Test
    void list_sortedByDateDesc_thenLatestAddedFirst() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "A", food);
        insertTx("EXPENSE", "1", "2026-10-05", "B", food); // isti datum, dodata kasnije
        insertTx("EXPENSE", "1", "2026-10-07", "C", food);
        insertTx("EXPENSE", "1", "2026-10-01", "D", food);
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").value("C"))
                .andExpect(jsonPath("$.items[1].description").value("B"))
                .andExpect(jsonPath("$.items[2].description").value("A"))
                .andExpect(jsonPath("$.items[3].description").value("D"));
    }

    @Test
    void list_empty_returnsEmptyPage() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void list_defaults() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "A", food);
        mvc.perform(get("/transactions"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void list_pagination_firstMiddleLastAndOutOfRange() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insertTx("EXPENSE", "1", "2026-10-0" + i, "T" + i, food);
        }
        mvc.perform(get("/transactions?page=0&size=2"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].description").value("T5"))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/transactions?page=1&size=2"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].description").value("T3"));
        mvc.perform(get("/transactions?page=2&size=2"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("T1"));
        mvc.perform(get("/transactions?page=9&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(5));
    }

    @Test
    void list_invalidPaging_returns400() throws Exception {
        mvc.perform(get("/transactions?size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/transactions?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/transactions?size=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/transactions?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/transactions?page=abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/transactions?size=abc")).andExpect(status().isBadRequest());
    }

    @Test
    void list_maxSizeIsAllowed() throws Exception {
        mvc.perform(get("/transactions?size=100")).andExpect(status().isOk());
    }

    // ---------- T2.4 filteri ----------

    @Test
    void filter_dateRange_includesBothEnds() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-01", "before", food);
        insertTx("EXPENSE", "1", "2026-10-05", "onFrom", food);
        insertTx("EXPENSE", "1", "2026-10-07", "middle", food);
        insertTx("EXPENSE", "1", "2026-10-10", "onTo", food);
        insertTx("EXPENSE", "1", "2026-10-15", "after", food);
        mvc.perform(get("/transactions?from=2026-10-05&to=2026-10-10"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].description").value("onTo"))
                .andExpect(jsonPath("$.items[2].description").value("onFrom"));
    }

    @Test
    void filter_onlyFrom_andOnlyTo() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-01", "A", food);
        insertTx("EXPENSE", "1", "2026-10-10", "B", food);
        insertTx("EXPENSE", "1", "2026-10-20", "C", food);
        mvc.perform(get("/transactions?from=2026-10-10")).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/transactions?to=2026-10-10")).andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void filter_fromAfterTo_returns400() throws Exception {
        mvc.perform(get("/transactions?from=2026-10-10&to=2026-10-01")).andExpect(status().isBadRequest());
    }

    @Test
    void filter_invalidDate_returns400() throws Exception {
        mvc.perform(get("/transactions?from=abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/transactions?to=2026-02-30")).andExpect(status().isBadRequest());
    }

    @Test
    void filter_byCategory() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "inFood", food);
        insertTx("EXPENSE", "1", "2026-10-05", "inFun", fun);
        mvc.perform(get("/transactions?categoryId=" + fun))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].description").value("inFun"));
    }

    @Test
    void filter_byType_caseInsensitive_andInvalidType() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "exp", food);
        insertTx("INCOME", "1", "2026-10-05", "inc", food);
        mvc.perform(get("/transactions?type=income"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].description").value("inc"));
        mvc.perform(get("/transactions?type=nesto")).andExpect(status().isBadRequest());
    }

    @Test
    void filter_byText_ignoresCase_andSkipsNullDescriptions() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "Big lunch", food);
        insertTx("EXPENSE", "1", "2026-10-05", null, food);
        insertTx("EXPENSE", "1", "2026-10-05", "Dinner", food);
        mvc.perform(get("/transactions?q=LUNCH"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Big lunch"));
    }

    @Test
    void filter_byText_percentAndUnderscoreAreLiteral() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "50% off", food);
        insertTx("EXPENSE", "1", "2026-10-05", "500 off", food);
        insertTx("EXPENSE", "1", "2026-10-05", "a_b", food);
        insertTx("EXPENSE", "1", "2026-10-05", "axb", food);
        mvc.perform(get("/transactions").param("q", "%"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].description").value("50% off"));
        mvc.perform(get("/transactions").param("q", "a_b"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].description").value("a_b"));
    }

    @Test
    void filter_blankText_isIgnored() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "A", food);
        insertTx("EXPENSE", "1", "2026-10-05", null, food);
        mvc.perform(get("/transactions?q=")).andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void filter_allCombined() throws Exception {
        insertTx("EXPENSE", "1", "2026-10-05", "Lunch at work", food); // pogadja sve
        insertTx("EXPENSE", "1", "2026-10-05", "Lunch at work", fun); // druga kategorija
        insertTx("INCOME", "1", "2026-10-05", "Lunch at work", food); // drugi tip
        insertTx("EXPENSE", "1", "2026-09-05", "Lunch at work", food); // van opsega
        insertTx("EXPENSE", "1", "2026-10-05", "Dinner", food); // drugi tekst
        mvc.perform(get("/transactions?from=2026-10-01&to=2026-10-31&categoryId=" + food
                + "&type=expense&q=lunch"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void filter_workWithPagination_totalsAreOverFilteredSet() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insertTx("EXPENSE", "1", "2026-10-0" + i, "food" + i, food);
        }
        insertTx("EXPENSE", "1", "2026-10-09", "other", fun);
        mvc.perform(get("/transactions?categoryId=" + food + "&size=2&page=2"))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));
    }
}