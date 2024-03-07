package org.example.profitpilot;

import org.example.profitpilot.api.ApiClient;
import org.example.profitpilot.database.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AppStartUpRunner implements CommandLineRunner {

    private final DatabaseService dbService;
    private final ApiClient apiClient;

    @Autowired
    public AppStartUpRunner(DatabaseService dbService, ApiClient apiClient) {
        this.dbService = dbService;
        this.apiClient = apiClient;
    }

    @Override
    public void run(String... args) throws Exception {
        apiClient.fetchDailyTimeSeries("IBM");
    }
}
