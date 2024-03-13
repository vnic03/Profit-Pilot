package org.example.profitpilot.api.marketaux;

import java.util.List;

public record NewsApiResponse(List<NewsArticle> articles) {

    public record NewsArticle(String title, String publicationDate, String description, String url, String imageUrl) { }
}
