package org.example.profitpilot.controller;

import java.util.List;

import org.example.profitpilot.api.marketaux.MarketauxCache;
import org.example.profitpilot.api.marketaux.NewsApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsDataController {

    private final MarketauxCache marketauxCache;

    public NewsDataController(MarketauxCache marketauxCache) {
        this.marketauxCache = marketauxCache;
    }

    public record NewsResult(
            String status,
            List<NewsApiResponse.NewsArticle> articles) {
    }

    @GetMapping
    public ResponseEntity<NewsResult> getNews(
            @RequestParam String query) {

        if (!marketauxCache.isExternalApiEnabled()) {
            return ResponseEntity.ok(
                    new NewsResult("disabled", List.of())
            );
        }

        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new NewsResult("invalid_query", List.of())
            );
        }

        NewsApiResponse response = marketauxCache.getData(query.trim());

        if (response == null || response.articles() == null) {
            return ResponseEntity.status(502).body(
                    new NewsResult("error", List.of())
            );
        }

        return ResponseEntity.ok(
                new NewsResult("ready", response.articles())
        );
    }
}
