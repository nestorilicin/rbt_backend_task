package com.example.backend;

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
class CategoryApiTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;   // samo za pripremu i proveru podataka u testu

    @BeforeEach
    void cleanDb() {
        jdbc.update("DELETE FROM budget");
        jdbc.update("DELETE FROM app_transaction");
        jdbc.update("DELETE FROM category");
    }

    private static String json(String name, String color) {
        return "{\"name\":\"%s\",\"color\":\"%s\"}".formatted(name, color);
    }

    private void postCategory(String name, String color, int expectedStatus) throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(json(name, color)))
                .andExpect(status().is(expectedStatus));
    }

    private long createCategory(String name, String color) throws Exception {
        postCategory(name, color, 201);
        return jdbc.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name.strip());
    }

    private void putCategory(String path, String name, String color, int expectedStatus) throws Exception {
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(json(name, color)))
                .andExpect(status().is(expectedStatus));
    }

    private void addTransaction(long categoryId) {
        jdbc.update("INSERT INTO app_transaction (type, amount, date, description, category_id) "
                + "VALUES ('EXPENSE', 10.00, '2026-10-01', 'test', ?)", categoryId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---------- T1.1 kreiranje ----------

    @Test
    void create_trimsName() throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON)
                        .content(json("  Food  ", "#FF0000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Food"));
        org.junit.jupiter.api.Assertions.assertEquals(1, count("SELECT COUNT(*) FROM category WHERE name = 'Food'"));
    }

    @Test
    void create_duplicateNameDifferentCase_returns409() throws Exception {
        createCategory("Food", "#FF0000");
        postCategory("food", "#00FF00", 409);
        postCategory("FOOD", "#00FF00", 409);
    }

    @Test
    void create_blankName_returns400() throws Exception {
        postCategory("", "#FF0000", 400);
        postCategory("   ", "#FF0000", 400);
    }

    @Test
    void create_nameLengthBoundary() throws Exception {
        postCategory("a".repeat(30), "#FF0000", 201);
        postCategory("b".repeat(31), "#FF0000", 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FF0000", "#FFF", "#GGGGGG", "#FF00001", ""})
    void create_invalidColor_returns400(String color) throws Exception {
        postCategory("Food", color, 400);
    }

    @Test
    void create_validLowercaseHexColor_returns201() throws Exception {
        postCategory("Food", "#abcdef", 201);
    }

    @Test
    void create_missingFields_returns400() throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content("{pokvaren"))
                .andExpect(status().isBadRequest());
    }

    // ---------- T1.2 izmena ----------

    @Test
    void update_changeOnlyColor_doesNotConflictWithItself() throws Exception {
        long id = createCategory("Food", "#FF0000");
        putCategory("/categories/" + id, "Food", "#0000FF", 200);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                count("SELECT COUNT(*) FROM category WHERE id = ? AND color = '#0000FF'", id));
    }

    @Test
    void update_changeOnlyCaseOfOwnName_returns200() throws Exception {
        long id = createCategory("Food", "#FF0000");
        putCategory("/categories/" + id, "FOOD", "#FF0000", 200);
    }

    @Test
    void update_trimsName() throws Exception {
        long id = createCategory("Food", "#FF0000");
        putCategory("/categories/" + id, "  Meals  ", "#FF0000", 200);
        org.junit.jupiter.api.Assertions.assertEquals(1, count("SELECT COUNT(*) FROM category WHERE name = 'Meals'"));
    }

    @Test
    void update_toAnotherCategorysName_returns409() throws Exception {
        long food = createCategory("Food", "#FF0000");
        createCategory("Fun", "#00FF00");
        putCategory("/categories/" + food, "fun", "#FF0000", 409);
    }

    @Test
    void update_invalidInput_returns400() throws Exception {
        long id = createCategory("Food", "#FF0000");
        putCategory("/categories/" + id, "", "#FF0000", 400);
        putCategory("/categories/" + id, "Food", "FF0000", 400);
    }

    @Test
    void update_nonExistent_returns404() throws Exception {
        putCategory("/categories/999999", "X", "#FF0000", 404);
    }

    @Test
    void update_nonNumericId_returns400() throws Exception {
        putCategory("/categories/abc", "X", "#FF0000", 400);
    }

    // ---------- T1.3 lista i pojedinacna ----------

    @Test
    void list_empty_returnsEmptyArray() throws Exception {
        mvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_sortedByNameIgnoringCase() throws Exception {
        createCategory("cherry", "#FF0000");
        createCategory("Banana", "#FF0000");
        createCategory("apple", "#FF0000");
        mvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("apple"))
                .andExpect(jsonPath("$[1].name").value("Banana"))
                .andExpect(jsonPath("$[2].name").value("cherry"));
    }

    @Test
    void get_existing_returns200() throws Exception {
        long id = createCategory("Food", "#FF0000");
        mvc.perform(get("/categories/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.color").value("#FF0000"));
    }

    @Test
    void get_nonExistent_returns404() throws Exception {
        mvc.perform(get("/categories/999999")).andExpect(status().isNotFound());
    }

    @Test
    void get_nonNumericId_returns400() throws Exception {
        mvc.perform(get("/categories/abc")).andExpect(status().isBadRequest());
    }

    // ---------- T1.4 brisanje ----------

    @Test
    void delete_withoutTransactions_returns204() throws Exception {
        long id = createCategory("Food", "#FF0000");
        mvc.perform(delete("/categories/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/categories/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void delete_withTransactions_returns409_andKeepsCategory() throws Exception {
        long id = createCategory("Food", "#FF0000");
        addTransaction(id);
        mvc.perform(delete("/categories/" + id)).andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(1, count("SELECT COUNT(*) FROM category WHERE id = ?", id));
    }

    @Test
    void delete_alsoDeletesBudgets() throws Exception {
        long id = createCategory("Food", "#FF0000");
        jdbc.update("INSERT INTO budget (month, category_id, amount) VALUES ('2026-10-01', ?, 100.00)", id);
        mvc.perform(delete("/categories/" + id)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(0, count("SELECT COUNT(*) FROM budget WHERE category_id = ?", id));
    }

    @Test
    void delete_nonExistent_returns404() throws Exception {
        mvc.perform(delete("/categories/999999")).andExpect(status().isNotFound());
    }

    @Test
    void delete_nonNumericId_returns400() throws Exception {
        mvc.perform(delete("/categories/abc")).andExpect(status().isBadRequest());
    }
}