package io.github.vnic03.profitpilot.api.marketaux;

import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.github.vnic03.profitpilot.api.CacheBase;
import io.github.vnic03.profitpilot.catalog.StockCatalog;
import io.github.vnic03.profitpilot.database.DatabaseService;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarketauxCache extends CacheBase<NewsApiResponse> {

  @Value("${profitpilot.marketaux.enabled:false}")
  private boolean providerEnabled;

  private final DatabaseService database;

  public MarketauxCache(DatabaseService databaseService) {
    super();
    this.database = databaseService;
    this.apiKey = dotenv.get("MARKETAUX_API_KEY");

    this.cache =
        CacheBuilder.newBuilder().maximumSize(200).expireAfterWrite(30, TimeUnit.MINUTES).build();
  }

  @Override
  protected boolean isProviderEnabled() {
    return providerEnabled;
  }

  @Override
  public NewsApiResponse fetchData(String symbol) {
    String normalizedSymbol = StockCatalog.normalize(symbol);

    if (!StockCatalog.supports(normalizedSymbol)) {
      return null;
    }

    if (!isExternalApiEnabled()) {
      return null;
    }

    if (apiKey == null || apiKey.isBlank()) {
      logger.error("Marketaux API key is missing.");
      return null;
    }

    String uri =
        "https://api.marketaux.com/v1/news/all"
            + "?api_token="
            + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
            + "&symbols="
            + URLEncoder.encode(normalizedSymbol, StandardCharsets.UTF_8)
            + "&filter_entities=true"
            + "&group_similar=true"
            + "&language=en";

    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(uri)).timeout(Duration.ofSeconds(20)).build();

    // Reserve before sending; failed calls may also consume the provider quota.
    boolean reserved;

    try {
      reserved = database.tryReserveNewsRequest(normalizedSymbol);
    } catch (SQLException e) {
      logger.error("Could not reserve news budget for {}.", normalizedSymbol);
      return null;
    }

    if (!reserved) {
      throw new NewsBudgetExceededException();
    }

    try {
      HttpResponse<String> response = sendExternalRequest(request);

      logger.info("News response for {}: HTTP {}.", normalizedSymbol, response.statusCode());

      if (response.statusCode() != 200) {
        return null;
      }

      JSONObject body = new JSONObject(response.body());
      JSONArray data = body.optJSONArray("data");

      if (data == null) {
        logger.warn("News response for {} has no article array.", normalizedSymbol);
        return null;
      }

      List<NewsApiResponse.NewsArticle> articles = new ArrayList<>();
      Set<String> seenUrls = new HashSet<>();

      for (int index = 0; index < data.length(); index++) {
        JSONObject article = data.getJSONObject(index);

        String title = article.optString("title", "").trim();
        String url = article.optString("url", "").trim();

        if (title.isBlank() || !isWebUrl(url) || !seenUrls.add(url)) {
          continue;
        }

        String source = article.optString("source", "").trim();

        if (source.isBlank()) {
          source = URI.create(url).getHost();
        }

        articles.add(
            new NewsApiResponse.NewsArticle(
                title,
                article.optString("published_at", ""),
                article.optString("description", ""),
                url,
                article.optString("image_url", null),
                source));
      }

      return new NewsApiResponse(List.copyOf(articles));

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn("News request interrupted for {}.", normalizedSymbol);
      return null;
    } catch (IOException e) {
      logger.warn(
          "News request failed for {}: {}.", normalizedSymbol, e.getClass().getSimpleName());
      return null;
    } catch (RuntimeException e) {
      logger.warn(
          "Could not process news for {}: {}.", normalizedSymbol, e.getClass().getSimpleName());
      return null;
    }
  }

  @Override
  public NewsApiResponse getData(String symbol) {
    String normalizedSymbol = StockCatalog.normalize(symbol);

    if (!StockCatalog.supports(normalizedSymbol) || !isExternalApiEnabled()) {
      return null;
    }

    try {
      // Share one load per symbol so simultaneous page views reuse the result.
      return cache.get(normalizedSymbol, () -> fetchData(normalizedSymbol));
    } catch (ExecutionException | UncheckedExecutionException e) {
      if (e.getCause() instanceof NewsBudgetExceededException exhausted) {
        throw exhausted;
      }

      logger.warn("Could not load cached news for {}.", normalizedSymbol);
      return null;
    }
  }

  private boolean isWebUrl(String value) {
    try {
      URI uri = URI.create(value);

      return uri.getHost() != null
          && ("https".equalsIgnoreCase(uri.getScheme())
              || "http".equalsIgnoreCase(uri.getScheme()));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
