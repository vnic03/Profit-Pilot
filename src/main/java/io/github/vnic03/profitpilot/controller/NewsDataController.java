package io.github.vnic03.profitpilot.controller;

import io.github.vnic03.profitpilot.api.marketaux.MarketauxCache;
import io.github.vnic03.profitpilot.api.marketaux.NewsApiResponse;
import io.github.vnic03.profitpilot.api.marketaux.NewsBudgetExceededException;
import java.time.Instant;
import java.util.List;
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
            String status, List<NewsApiResponse.NewsArticle> articles, Instant fetchedAt) {}

    @GetMapping
    public ResponseEntity<?> getNews(
            @RequestParam(name = "symbol", defaultValue = "") String symbol) {

        String normalizedSymbol = ApiSupport.requireSymbol(symbol);

        if (!marketauxCache.isExternalApiEnabled()) {
            return ResponseEntity.ok(new NewsResult("disabled", List.of(), null));
        }

        NewsApiResponse response;

        try {
            response = marketauxCache.getData(normalizedSymbol);
        } catch (NewsBudgetExceededException e) {
            return ApiSupport.error(429, "budget_exhausted", "Newsrequest budget is exhausted,");
        }

        if (response == null || response.articles() == null) {
            return ApiSupport.error(502, "news_failed", "Could not load news.");
        }

        return ResponseEntity.ok(
                new NewsResult("ready", response.articles(), response.fetchedAt()));
    }
}
