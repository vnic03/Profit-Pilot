package org.example.profitpilot.api;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlphaVantageCache {

    private final Dotenv dotenv = Dotenv.load();
    private final String apiKey = dotenv.get("ALPHA_VANTAGE_API_KEY");
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final LoadingCache<String, ApiResponse> cache;
    private final Logger logger = LoggerFactory.getLogger(AlphaVantageCache.class);

    public AlphaVantageCache() {
        cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.DAYS)
                .build(new CacheLoader<>() {
                    @Override
                    public ApiResponse load(@NonNull String key) {
                        return fetchDataFromAlphaVantage(key);
                    }
                });
    }

    public ApiResponse getData(String key) {
        return cache.getUnchecked(key);
    }

    private ApiResponse fetchDataFromAlphaVantage(String symbol) {
        final String uri = String.format(
                "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                symbol, apiKey
        );
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Request error: HTTP status code {}", response.statusCode());
                return null;
            }

            String responseBody = response.body();
            JSONObject jsonObject = new JSONObject(responseBody);

            if (!jsonObject.has("Time Series (Daily)")) {
                logger.error("Response does not have 'Time Series (Daily)' field.");
                return null;
            }

            JSONObject timeSeries = jsonObject.getJSONObject("Time Series (Daily)");
            Map<String, ApiResponse.DailyData> timeSeriesMap = new HashMap<>();

            for (String date : timeSeries.keySet()) {
                JSONObject dailyDataJson = timeSeries.getJSONObject(date);

                double open = dailyDataJson.getDouble("1. open");
                double high = dailyDataJson.getDouble("2. high");
                double low = dailyDataJson.getDouble("3. low");
                double close = dailyDataJson.getDouble("4. close");
                long volume = dailyDataJson.getLong("5. volume");

                ApiResponse.DailyData dailyData = new ApiResponse.DailyData(open, high, low, close, volume);
                timeSeriesMap.put(date, dailyData);
            }
            return new ApiResponse(timeSeriesMap);

        } catch (IOException e) {
            logger.error("IOException occurred while fetching daily time series", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted Exception occurred while fetching daily time series", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("An unexpected error occurred while fetching daily time series", e);
        }
        return null;
    }
}
