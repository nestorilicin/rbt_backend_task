package com.example.backend.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.db.dao.CategoryInterface;
import com.example.backend.models.Category;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryInterface repo;

    public CategoryController(CategoryInterface repo) {
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<Category> addCategory(@RequestBody Category category) throws SQLException {
        // trimmed before validation, so a name with only spaces counts as empty.
        String name = category.getName() == null ? null : category.getName().strip();
        String color = category.getColor() == null ? null : category.getColor().strip();

        validate(name, color);

        if (repo.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category with name '" + name + "' already exists");
        }

        Category createdCategory = repo.insert(name, color);
        return ResponseEntity.created(URI.create("/categories/" + createdCategory.getId())).body(createdCategory);
    }

    // check order: validation (400), existence (404), duplicate name (409).
    // UNIQUE constraint in the database still catches concurrent duplicates.
    @PutMapping("/{id}")
    public Category updateCategory(@PathVariable long id, @RequestBody Category category) throws SQLException {
        // trimmed before validation, so a name with only spaces counts as empty.
        String name = category.getName() == null ? null : category.getName().strip();
        String color = category.getColor() == null ? null : category.getColor().strip();

        validate(name, color);

        if (repo.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category " + id + " not found");
        }
        if (repo.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category with name '" + name + "' already exists");
        }

        repo.update(id, name, color);
        return new Category(id, name, color);
    }

    @GetMapping
    public List<Category> getAllCategories() throws SQLException {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Category getCategory(@PathVariable long id) throws SQLException {
        Category category = repo.findById(id);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category " + id + " not found");
        }
        return category;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) throws SQLException {
        if (repo.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category " + id + " not found");
        }
        if (repo.hasTransactions(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category " + id + " has transactions and cannot be deleted");
        }
        repo.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void validate(String name, String color) {
        if (name == null || name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (name.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must be 1 to 30 characters");
        }
        if (color == null || !color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "color must be in the form #RRGGBB");
        }
    }
}
