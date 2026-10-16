package io.github.vnic03.profitpilot.controller;

import io.github.vnic03.profitpilot.database.DatabaseService;

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
