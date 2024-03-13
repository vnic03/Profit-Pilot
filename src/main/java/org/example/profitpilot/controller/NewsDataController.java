package org.example.profitpilot.controller;

import org.example.profitpilot.api.marketaux.MarketauxCache;
import org.example.profitpilot.api.marketaux.NewsApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsDataController {

    private final MarketauxCache marketauxCache;

    @Autowired
    public NewsDataController(MarketauxCache marketauxCache) {
        this.marketauxCache = marketauxCache;
    }

    @GetMapping
    public ResponseEntity<List<NewsApiResponse.NewsArticle>> getNews(@RequestParam String query) {
        NewsApiResponse response = marketauxCache.getData(query);
        if (response == null || response.articles().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response.articles());
    }
}
