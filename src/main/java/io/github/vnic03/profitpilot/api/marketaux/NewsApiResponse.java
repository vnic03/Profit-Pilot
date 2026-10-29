package io.github.vnic03.profitpilot.api.marketaux;

import java.time.Instant;
import java.util.List;

public record NewsApiResponse(List<NewsArticle> articles, Instant fetchedAt) {

    public NewsApiResponse(List<NewsArticle> articles) {
        this(articles, Instant.now());
    }

    public record NewsArticle(
            String title,
            String publicationDate,
            String description,
            String url,
            String imageUrl,
            String source) {}
}
