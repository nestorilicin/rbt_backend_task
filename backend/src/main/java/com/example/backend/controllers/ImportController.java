package com.example.backend.controllers;

import com.example.backend.importer.CsvImporter;
import com.example.backend.models.ImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

@RestController
@RequestMapping("/transactions/import")
public class ImportController {

    private final CsvImporter importer;

    public ImportController(CsvImporter importer) {
        this.importer = importer;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws SQLException, IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        try (InputStream in = file.getInputStream()) {
            return importer.importCsv(in);
        }
    }
}