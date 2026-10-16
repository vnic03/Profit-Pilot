package io.github.vnic03.profitpilot.controller;

import java.time.Instant;
import java.util.List;

import io.github.vnic03.profitpilot.api.marketaux.MarketauxCache;
import io.github.vnic03.profitpilot.api.marketaux.NewsApiResponse;
import io.github.vnic03.profitpilot.catalog.StockCatalog;
import io.github.vnic03.profitpilot.api.marketaux.NewsBudgetExceededException;

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
            List<NewsApiResponse.NewsArticle> articles,
            Instant fetchedAt) {
    }

    @GetMapping
    public ResponseEntity<NewsResult> getNews(
            @RequestParam(name = "symbol", defaultValue = "")
            String symbol) {

        String normalizedSymbol = StockCatalog.normalize(symbol);

        if (!StockCatalog.supports(normalizedSymbol)) {
            return ResponseEntity.badRequest().body(
                    new NewsResult(
                            "invalid_symbol",
                            List.of(),
                            null
                    )
            );
        }

        if (!marketauxCache.isExternalApiEnabled()) {
            return ResponseEntity.ok(
                    new NewsResult("disabled", List.of(), null)
            );
        }

        NewsApiResponse response;

        try {
            response = marketauxCache.getData(normalizedSymbol);
        } catch (NewsBudgetExceededException e) {
            return ResponseEntity.status(429).body(
                    new NewsResult(
                            "budget_exhausted",
                            List.of(),
                            null
                    )
            );
        }


        if (response == null || response.articles() == null) {
            return ResponseEntity.status(502).body(
                    new NewsResult("error", List.of(), null)
            );
        }

        return ResponseEntity.ok(
                new NewsResult(
                        "ready",
                        response.articles(),
                        response.fetchedAt()
                )
        );
    }
}
