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
class BudgetApiTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

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

    private long insertCategory(String name) {
        jdbc.update("INSERT INTO category (name, color) VALUES (?, '#FF0000')", name);
        return jdbc.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private void setBudget(String month, long categoryId, String rawAmount, int expectedStatus) throws Exception {
        mvc.perform(put("/budgets/" + month + "/" + categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + rawAmount + "}"))
                .andExpect(status().is(expectedStatus));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---------- T3.1 postavljanje / izmena ----------

    @Test
    void set_firstTime_createsBudget() throws Exception {
        mvc.perform(put("/budgets/2026-10/" + food)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":300.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.categoryId").value(food))
                .andExpect(jsonPath("$.amount").value(300.0));
        assertEquals(1, count("SELECT COUNT(*) FROM budget"));
    }

    @Test
    void set_secondTime_updatesInsteadOfDuplicating() throws Exception {
        setBudget("2026-10", food, "300.00", 200);
        setBudget("2026-10", food, "150.50", 200);
        assertEquals(1, count("SELECT COUNT(*) FROM budget"));
        assertEquals(1, count("SELECT COUNT(*) FROM budget WHERE amount = 150.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.5", "12", "1000000.00", "0.01"})
    void set_validAmounts_return200(String amount) throws Exception {
        setBudget("2026-10", food, amount, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.001", "12.345", "1000000.01", "\"abc\"", "null"})
    void set_invalidAmounts_return400(String amount) throws Exception {
        setBudget("2026-10", food, amount, 400);
        assertEquals(0, count("SELECT COUNT(*) FROM budget"));
    }

    @Test
    void set_missingAmount_returns400() throws Exception {
        mvc.perform(put("/budgets/2026-10/" + food)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void set_malformedJson_returns400() throws Exception {
        mvc.perform(put("/budgets/2026-10/" + food)
                        .contentType(MediaType.APPLICATION_JSON).content("{pokvaren"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "abc", "2026-1", "2026-10-01", "202610"})
    void set_invalidMonth_returns400(String month) throws Exception {
        setBudget(month, food, "100", 400);
    }

    @Test
    void set_nonExistentCategory_returns404() throws Exception {
        setBudget("2026-10", 999999, "100", 404);
    }

    @Test
    void set_nonNumericCategoryId_returns400() throws Exception {
        mvc.perform(put("/budgets/2026-10/abc")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void set_differentMonthsAndCategories_areIndependent() throws Exception {
        setBudget("2026-10", food, "100", 200);
        setBudget("2026-11", food, "200", 200);   // ista kategorija, drugi mesec
        setBudget("2026-10", fun, "300", 200);    // isti mesec, druga kategorija
        assertEquals(3, count("SELECT COUNT(*) FROM budget"));
    }

    // ---------- T3.2 uklanjanje ----------

    @Test
    void delete_removesOnlyThatMonthAndCategory() throws Exception {
        setBudget("2026-10", food, "100", 200);
        setBudget("2026-11", food, "200", 200);
        setBudget("2026-10", fun, "300", 200);
        mvc.perform(delete("/budgets/2026-10/" + food)).andExpect(status().isNoContent());
        assertEquals(2, count("SELECT COUNT(*) FROM budget"));
        assertEquals(0, count("SELECT COUNT(*) FROM budget WHERE category_id = ? AND month = '2026-10-01'", food));
    }

    @Test
    void delete_nonExistentBudget_returns404() throws Exception {
        mvc.perform(delete("/budgets/2026-10/" + food)).andExpect(status().isNotFound());
    }

    @Test
    void delete_invalidMonth_returns400() throws Exception {
        mvc.perform(delete("/budgets/2026-13/" + food)).andExpect(status().isBadRequest());
    }

    @Test
    void delete_budgetIsGoneFromList() throws Exception {
        setBudget("2026-10", food, "100", 200);
        mvc.perform(delete("/budgets/2026-10/" + food)).andExpect(status().isNoContent());
        mvc.perform(get("/budgets").param("month", "2026-10"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- T3.3 lista ----------

    @Test
    void list_returnsOnlyRequestedMonth() throws Exception {
        setBudget("2026-10", food, "100", 200);
        setBudget("2026-10", fun, "50", 200);
        setBudget("2026-11", food, "999", 200);
        mvc.perform(get("/budgets").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].month").value("2026-10"))
                .andExpect(jsonPath("$[1].month").value("2026-10"));
    }

    @Test
    void list_emptyMonth_returnsEmptyArray() throws Exception {
        mvc.perform(get("/budgets").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_missingOrInvalidMonth_returns400() throws Exception {
        mvc.perform(get("/budgets")).andExpect(status().isBadRequest());
        mvc.perform(get("/budgets").param("month", "2026-13")).andExpect(status().isBadRequest());
        mvc.perform(get("/budgets").param("month", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/budgets").param("month", "")).andExpect(status().isBadRequest());
    }
}