package org.example.profitpilot.controller;

import org.example.profitpilot.database.DatabaseService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsBudgetController {

    private final DatabaseService database;

    public NewsBudgetController(DatabaseService database) {
        this.database = database;
    }

    @GetMapping("/budget")
    public DatabaseService.ImportBudgetStatus getBudget() {
        return database.getNewsBudgetStatus();
    }
}
