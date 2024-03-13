package org.example.profitpilot.api.marketaux;

import org.example.profitpilot.api.CacheBase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketauxCache extends CacheBase<NewsApiResponse> {

    public MarketauxCache() {
        super();
        this.apiKey = dotenv.get("MARKETAUX_API_KEY");
    }

    @Override
    public NewsApiResponse fetchData(String query) {
        final String uri = String.format(
                "https://api.marketaux.com/v1/news/all?api_token=%s&search=%s",
                apiKey, query.replace(" ", "%20")
        );
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("API Response for query {}: {}", query, response.body());

            if (response.statusCode() != 200) {
                logger.error("Request error for query {}: HTTP status code {}", query, response.statusCode());
                return null;
            }

            JSONObject jsonObject = new JSONObject(response.body());
            if (!jsonObject.has("data")) {
                logger.error("Response for query {} does not have 'data' field.", query);
                return null;
            }

            JSONArray articlesJson = jsonObject.getJSONArray("data");
            List<NewsApiResponse.NewsArticle> articles = new ArrayList<>();

            for (int i = 0; i < articlesJson.length(); i++) {
                JSONObject articleJson = articlesJson.getJSONObject(i);

                String title = articleJson.optString("title", "No title");
                String publicationDate = articleJson.optString("published_at", "No publication date");
                String description = articleJson.optString("description", "No description");
                String url = articleJson.optString("url", "#");
                String imageUrl = articleJson.optString("image_url", null);

                NewsApiResponse.NewsArticle article = new NewsApiResponse.NewsArticle(title, publicationDate, description, url, imageUrl);
                articles.add(article);
            }

            return new NewsApiResponse(articles);

        } catch (IOException | InterruptedException e) {
            logger.error("Error occurred while fetching news for query {}", query, e);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.error("An unexpected error occurred while fetching news for query {}", query, e);
            return null;
        }
    }
}
