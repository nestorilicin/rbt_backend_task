package com.example.backend.controllers;

import com.example.backend.db.dao.SummaryInterface;
import com.example.backend.models.MonthlySummary;
import com.example.backend.models.Totals;
import com.example.backend.rules.MonthRules;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.YearMonth;

@RestController
@RequestMapping("/summary")
public class SummaryController {

    private final SummaryInterface repo;

    public SummaryController(SummaryInterface repo) {
        this.repo = repo;
    }

    @GetMapping
    public MonthlySummary get(@RequestParam String month) throws SQLException {
        YearMonth ym;
        try {
            ym = MonthRules.parse(month);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        Totals t = repo.totals(ym);
        var income = t.income().setScale(2);
        var expense = t.expense().setScale(2);
        return new MonthlySummary(ym.toString(), income, expense, income.subtract(expense),
                repo.categories(ym));
    }
}